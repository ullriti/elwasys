package org.kabieror.elwasys.raspiclient.application;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.api.ApiClient;
import org.kabieror.elwasys.raspiclient.offline.OfflineGateway;
import org.kabieror.elwasys.raspiclient.offline.OfflineJournal;
import org.kabieror.elwasys.raspiclient.offline.OfflineSnapshotStore;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Client-Test für Phase 4 AP6, Abnahme-Szenario (d) "Replay-Idempotenz (doppelte Meldung →
 * keine Doppelbuchung)" (siehe kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am
 * Terminal" Punkt 4 und kb/08-test-plan.md).
 * <p>
 * <b>Bewusst kein TestFX/JavaFX-E2E-Test</b> (kleinste belastbare Testform, siehe Auftrag):
 * das Szenario "ein Journal-Eintrag wurde beim Backend bereits erfolgreich verarbeitet, aber
 * das Terminal weiß es (noch) nicht (Absturz zwischen erfolgreichem Netzwerkaufruf und
 * {@code OfflineJournal#clear()}, oder ein zweiter, sich überschneidender Replay-Versuch)"
 * lässt sich direkt und deterministisch auf {@code offline.OfflineGateway#replay()} + einem
 * gegen das gemeinsame Test-Backend geöffneten {@link ApiClient} nachbilden, ohne den vollen
 * App-/UI-Umweg zu brauchen (der reale Backend-seitige Dedup-Mechanismus selbst -
 * {@code Idempotency-Key}-Header/{@code IdempotencyService} - ist bereits umfassend über
 * {@code ExecutionControllerIdempotencyTest} im Backend-Modul abgedeckt; dieser Test prüft
 * gezielt die CLIENT-seitige Journal-Replay-Wiederholbarkeit, die dort nicht getestet werden
 * kann). Test-Backend-Verbindung/Token wie in den übrigen {@code *E2ETest}-Klassen über
 * {@link TestBackend}.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientOfflineReplayIdempotencyE2ETest)
 */
class ClientOfflineReplayIdempotencyE2ETest {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    @Test
    void replaying_the_same_journal_twice_does_not_double_book() throws Exception {
        int groupId;
        int deviceId;
        int programId;
        int userId;
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement()) {
            int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN "
                    + "(SELECT id FROM executions WHERE device_id IN "
                    + "(SELECT id FROM devices WHERE name LIKE 'E2E-Idem-%'))");
            s.executeUpdate(
                    "DELETE FROM executions WHERE device_id IN (SELECT id FROM devices WHERE name LIKE 'E2E-Idem-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-Idem-%'");
            s.executeUpdate("DELETE FROM programs WHERE name LIKE 'E2E-Idem-%'");

            programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, time_unit, "
                            + "auto_end, earliest_auto_end, enabled) VALUES ('E2E-Idem-Prog', 'FIXED', 3600, 0, "
                            + "1.50, NULL, NULL, FALSE, 0, TRUE) RETURNING id");
            deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, "
                            + "fhem_power_name, deconz_uuid, auto_end_power_threshold, auto_end_wait_time, "
                            + "enabled) VALUES ('E2E-Idem-WM', 1, " + locationId
                            + ", 'wm1', 'wm1sw', 'wm1pw', '', 0.5, 20, TRUE) RETURNING id");
            s.executeUpdate(
                    "INSERT INTO device_program_rel (device_id, program_id) VALUES (" + deviceId + ", " + programId
                            + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES (" + programId
                    + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES (" + deviceId + ", "
                    + groupId + ")");

            userId = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Idempotenz', 'e2e_idem_" + System.currentTimeMillis() + "', '9"
                            + String.format("%08d", System.currentTimeMillis() % 100_000_000L) + "', " + groupId
                            + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate(
                    "INSERT INTO credit_accounting (user_id, amount, description) VALUES (" + userId + ", 100, "
                            + "'E2E seed')");
        }

        Path journalFile = Files.createTempFile("elwasys-offline-idem-journal", ".jsonl");
        Files.deleteIfExists(journalFile); // OfflineJournal legt die Datei selbst bei Bedarf an
        Path snapshotFile = Files.createTempFile("elwasys-offline-idem-snapshot", ".json");
        Files.deleteIfExists(snapshotFile);

        ApiClient apiClient = new ApiClient(TestBackend.url(), TestBackend.token());
        OfflineJournal journal = new OfflineJournal(journalFile);
        OfflineGateway gateway = new OfflineGateway(apiClient, new OfflineSnapshotStore(snapshotFile), journal);

        String startKey = UUID.randomUUID().toString();
        String finishKey = UUID.randomUUID().toString();
        LocalDateTime clientTimestamp = LocalDateTime.now().minusMinutes(5);

        // Journal-Eintraege wie sie ExecutionFinisher/OfflineGateway#createExecution beim
        // Buchen+Beenden einer offline gebuchten Ausfuehrung hinterlegt haetten.
        journal.appendStart(startKey, clientTimestamp, userId, deviceId, programId);
        journal.appendFinish(false, finishKey, clientTimestamp.plusMinutes(1), userId, null, startKey,
                new BigDecimal("1.50"));

        // Erster Replay: legt die Ausfuehrung beim Backend an und beendet sie - erfolgreich,
        // Journal wird geleert.
        boolean firstReplay = gateway.replay();
        assertEquals(true, firstReplay, "The first replay should fully succeed");
        assertEquals(1, executionCountForUser(userId), "Exactly one execution should exist after the first replay");
        assertEquals(1, creditAccountingCountForUser(userId),
                "Exactly one credit accounting entry should exist after the first replay");

        // Simuliert entweder (a) einen Absturz des Terminals zwischen den bereits
        // erfolgreichen Netzwerkaufrufen und OfflineJournal#clear(), oder (b) einen zweiten,
        // sich ueberschneidenden Replay-Versuch mit DEMSELBEN Journal-Inhalt - beide Male
        // wird der Replay mit DENSELBEN Idempotenz-Schluesseln wiederholt.
        journal.appendStart(startKey, clientTimestamp, userId, deviceId, programId);
        journal.appendFinish(false, finishKey, clientTimestamp.plusMinutes(1), userId, null, startKey,
                new BigDecimal("1.50"));

        boolean secondReplay = gateway.replay();
        assertEquals(true, secondReplay, "The repeated replay should also succeed (it only re-delivers the "
                + "backend's already-stored idempotent responses)");
        assertEquals(1, executionCountForUser(userId),
                "The repeated replay with the SAME idempotency keys must not create a second execution");
        assertEquals(1, creditAccountingCountForUser(userId),
                "The repeated replay with the SAME idempotency keys must not book the price a second time");
    }

    private static int executionCountForUser(int userId) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT COUNT(*) FROM executions WHERE user_id=" + userId)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static int creditAccountingCountForUser(int userId) throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery(
                        "SELECT COUNT(*) FROM credit_accounting WHERE user_id=" + userId + " AND execution_id IS "
                                + "NOT NULL")) {
            r.next();
            return r.getInt(1);
        }
    }

    private static int queryInt(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static int insertReturningId(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }
}
