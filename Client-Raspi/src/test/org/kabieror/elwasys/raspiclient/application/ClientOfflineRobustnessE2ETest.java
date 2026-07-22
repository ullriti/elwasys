package org.kabieror.elwasys.raspiclient.application;

import javafx.scene.Node;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;

import javafx.scene.input.KeyCode;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client E2E für Phase 4 AP6 (Offline-Robustheit, siehe kb/05-migration-plan.md
 * "Konzeptskizze: Offline-Buchungen am Terminal" und kb/08-test-plan.md, C15-Nachfolger):
 * ein {@link BackendProxy} zwischen Terminal und dem gemeinsam genutzten Test-Backend
 * ({@link TestBackend}) macht "Backend nicht erreichbar" gezielt simulierbar, ohne das für
 * die ganze Suite laufende Backend selbst anzufassen.
 * <p>
 * Deckt die vier Abnahme-Szenarien des Auftrags ab:
 * <ol>
 *     <li>(a) Backend fällt während einer laufenden Ausführung aus → lokal beendet + nach
 *         Reconnect nachgemeldet ({@link #a_running_execution_survives_a_backend_outage_and_is_replayed()}).</li>
 *     <li>(b) Backend aus, innerhalb des Zeitfensters → neue Buchung offline akzeptiert,
 *         später repliziert ({@link #b_a_new_booking_is_accepted_offline_within_the_window_and_replayed()}).</li>
 *     <li>(c) Snapshot/Fenster abgelaufen → neue Buchung abgelehnt, C15-Fehlerbild
 *         ({@link #c_a_new_booking_is_rejected_once_the_offline_window_has_expired()}).</li>
 *     <li>(d) Replay-Idempotenz (doppelte Meldung → keine Doppelbuchung) - separater, gezielt
 *         leichtgewichtiger Test ohne TestFX, siehe {@code ClientOfflineReplayIdempotencyE2ETest}
 *         (Begründung dort).</li>
 * </ol>
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientOfflineRobustnessE2ETest)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClientOfflineRobustnessE2ETest {

    private static final int FHEM_PORT = 7083;
    private static final String DEVICE_NAME = "E2E-WM-Offline";
    private static final String PROGRAM_NAME = "E2E-Offline-Prog";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static BackendProxy proxy;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    private static int locationId;
    private static int deviceId;
    private static int programId;

    private static String cardIdA;
    private static int userIdA;
    private static String cardIdB;
    private static int userIdB;
    private static String cardIdC;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        URI backendUri = URI.create(TestBackend.url());
        proxy = new BackendProxy(backendUri.getHost(), backendUri.getPort());
        String proxyUrl = proxy.start();

        workDir = Files.createTempDirectory("elwasys-offline-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "backend.url=" + proxyUrl,
                "backend.token=" + TestBackend.token(),
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "instance.port=8282",
                "maintenance.server=localhost",
                "maintenance.port=3602",
                // Kurzes Intervall, damit der periodische Offline-Abgleich (Snapshot-
                // Aktualisierung + Journal-Replay) innerhalb der Testlaufzeit greift.
                "offline.pollIntervalSeconds=3",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-offline-client");
        System.setProperty("user.dir", workDir.toString());

        primaryStage = FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
        robot = new FxRobot();
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(45));

        // Sorge fuer einen frischen, nicht abgelaufenen Snapshot, bevor irgendein Test das
        // Backend offline schaltet (Konzeptskizze Punkt 1 "periodisch aktualisierter
        // Snapshot").
        assertTrue(waitUntil(() -> ElwaManager.instance.getOfflineGateway().hasUsableSnapshot(),
                Duration.ofSeconds(15)), "A usable offline snapshot should be available after a successful start");
    }

    @AfterAll
    static void shutdown() throws Exception {
        try {
            FxToolkit.cleanupStages();
        } catch (Exception ignored) {
            // best effort
        }
        if (fhem != null) {
            fhem.stop();
        }
        if (proxy != null) {
            proxy.stop();
        }
        // Standort-weite Aenderung aus Testfall (c) wieder rueckgaengig machen (siehe dort) -
        // andere/spaetere Testlaeufe teilen sich denselben Standort "Default".
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE locations SET offline_max_duration_minutes=60 WHERE id=" + locationId);
        } catch (Exception ignored) {
            // best effort
        }
    }

    // --- (a) laufende Ausführung übersteht einen Backend-Ausfall ---------------------------

    @Test
    @Order(1)
    void a_running_execution_survives_a_backend_outage_and_is_replayed() throws Exception {
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(10));

        loginWithCard(cardIdA);
        int executionId = bookDevice();
        assertTrue(waitUntil(() -> isFinishedInDb(executionId) == Boolean.FALSE, Duration.ofSeconds(15)),
                "The booked execution should be running (not finished) in the backend database");

        // Backend faellt waehrend der laufenden Ausfuehrung aus.
        proxy.goOffline();

        // Abbruch ueber die UI - der Live-Abort-Aufruf scheitert an einem reinen
        // Kommunikationsfehler; ExecutionFinisher schliesst die Ausfuehrung stattdessen
        // lokal ab und hinterlegt sie im Offline-Journal (Stufe A), statt einen
        // Fehler-/Retry-Zustand zu zeigen.
        assertTrue(waitUntil(() -> isVisible(buttonFor(DEVICE_NAME, ".abort-button")), Duration.ofSeconds(10)),
                "The abort button should be shown while the device is occupied");
        robot.clickOn(buttonFor(DEVICE_NAME, ".abort-button"));
        waitForState(MainFormState.CONFIRM_PROGRAM_ABORTION, Duration.ofSeconds(10));
        clickById("#forwardButton");

        assertTrue(waitUntil(() -> ElwaManager.instance.getExecutionManager().getRunningExecutions().isEmpty(),
                Duration.ofSeconds(15)), "The execution should be finished LOCALLY despite the backend outage");
        assertTrue(waitUntil(() -> ElwaManager.instance.getOfflineGateway().hasPendingJournalEntries(),
                Duration.ofSeconds(5)), "The abort should have been recorded in the offline journal");

        // Kein Datenverlust, aber auch keine Vorwegnahme: das Backend weiss noch nichts vom
        // Ende, solange es nicht erreichbar ist.
        assertEquals(Boolean.FALSE, isFinishedInDb(executionId),
                "The backend must not yet show the execution as finished while still offline");

        // Wiederverbindung: der periodische Abgleich meldet das Journal nach.
        proxy.goOnline();
        assertTrue(waitUntil(() -> !ElwaManager.instance.getOfflineGateway().hasPendingJournalEntries(),
                Duration.ofSeconds(20)), "The journal should be fully replayed after reconnecting");
        assertTrue(waitUntil(() -> isFinishedInDb(executionId) == Boolean.TRUE, Duration.ofSeconds(10)),
                "The backend should show the execution as finished after the journal replay");
    }

    // --- (b) neue Offline-Buchung innerhalb des Zeitfensters -------------------------------

    @Test
    @Order(2)
    void b_a_new_booking_is_accepted_offline_within_the_window_and_replayed() throws Exception {
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(15));
        int executionsBefore = executionCountForUser(userIdB);

        proxy.goOffline();

        // Kartenlogin UND Buchung laufen jetzt komplett gegen den lokalen Snapshot.
        loginWithCard(cardIdB);
        assertTrue(waitUntil(() -> isEnabled(buttonFor(DEVICE_NAME, ".select-button")), Duration.ofSeconds(10)),
                "The 'book device' button should be enabled (offline permission check via the snapshot)");
        robot.clickOn(buttonFor(DEVICE_NAME, ".select-button"));
        waitForState(MainFormState.CONFIRMATION, Duration.ofSeconds(10));
        clickById("#forwardButton");

        ClientDevice device = findManagedDeviceOffline(DEVICE_NAME);
        assertTrue(waitUntil(() -> {
            ClientExecution running = ElwaManager.instance.getExecutionManager().getRunningExecution(device);
            return running != null && running.isOfflinePendingReplay();
        }, Duration.ofSeconds(15)), "An offline booking should create a locally pending execution");

        // Die Buchung existiert bewusst noch NICHT beim Backend - sie wurde offline angelegt.
        assertEquals(executionsBefore, executionCountForUser(userIdB),
                "The backend must not know about the offline booking yet");

        // Beende die offline gebuchte Ausfuehrung (wird ebenfalls nur journaliert, nie live
        // versucht - siehe ClientExecution#isOfflinePendingReplay Javadoc).
        assertTrue(waitUntil(() -> isVisible(buttonFor(DEVICE_NAME, ".abort-button")), Duration.ofSeconds(10)),
                "The abort button should be shown while the device is occupied");
        robot.clickOn(buttonFor(DEVICE_NAME, ".abort-button"));
        waitForState(MainFormState.CONFIRM_PROGRAM_ABORTION, Duration.ofSeconds(10));
        clickById("#forwardButton");

        assertTrue(waitUntil(() -> ElwaManager.instance.getExecutionManager().getRunningExecutions().isEmpty(),
                Duration.ofSeconds(15)), "The offline booking should be finished locally");
        assertTrue(ElwaManager.instance.getOfflineGateway().hasPendingJournalEntries(),
                "The journal should contain the offline booking's START and FINISH entries");

        proxy.goOnline();
        assertTrue(waitUntil(() -> !ElwaManager.instance.getOfflineGateway().hasPendingJournalEntries(),
                Duration.ofSeconds(20)), "The journal should be fully replayed after reconnecting");
        assertTrue(waitUntil(() -> executionCountForUser(userIdB) == executionsBefore + 1, Duration.ofSeconds(10)),
                "Exactly one new, finished execution should exist at the backend after the replay");
        assertTrue(waitUntil(() -> creditAccountingCountForUser(userIdB) > 0, Duration.ofSeconds(10)),
                "The replay should have billed the offline booking through the backend's authoritative pricing");
    }

    // --- (c) Zeitfenster abgelaufen ----------------------------------------------------------

    @Test
    @Order(3)
    void c_a_new_booking_is_rejected_once_the_offline_window_has_expired() throws Exception {
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(15));

        // offline.max-duration auf 0 setzen (Portal-Standorte-Dialog-Aequivalent per SQL) und
        // sofort einen frischen Snapshot laden, statt auf den naechsten periodischen Abgleich
        // zu warten - der neu geladene Snapshot ist damit augenblicklich "abgelaufen".
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE locations SET offline_max_duration_minutes=0 WHERE id=" + locationId);
        }
        ElwaManager.instance.getOfflineGateway().refreshSnapshot();
        assertFalse(ElwaManager.instance.getOfflineGateway().hasUsableSnapshot(),
                "The snapshot should be considered expired once offline.max-duration is 0");

        proxy.goOffline();

        // Ein Kartenlogin-Versuch faellt jetzt auf denselben Kommunikationsfehler zurueck wie
        // ein generell nicht erreichbares Backend (C15-Fehlerbild) - kein neues, irrefuehrendes
        // Fehlerbild fuer einen abgelaufenen Snapshot.
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardIdC);
        robot.push(KeyCode.ENTER);

        assertTrue(waitUntil(() -> {
            MainFormController c = (MainFormController) ElwaManager.instance.getMainFormController();
            return c.getMainFormState() == MainFormState.ERROR;
        }, Duration.ofSeconds(15)), "A booking attempt with an expired snapshot should show the C15 error state");

        proxy.goOnline();
    }

    // --- helpers ------------------------------------------------------------

    private static void loginWithCard(String cardId) throws InterruptedException {
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);
        MainFormController controller = (MainFormController) ElwaManager.instance.getMainFormController();
        assertTrue(waitUntil(() -> controller.getRegisteredUser() != null, Duration.ofSeconds(10)),
                "Scanning a known card should log the user in");
    }

    /** Books {@link #DEVICE_NAME} and returns the backend execution id. */
    private static int bookDevice() throws InterruptedException {
        assertTrue(waitUntil(() -> isEnabled(buttonFor(DEVICE_NAME, ".select-button")), Duration.ofSeconds(10)),
                "The 'book device' button should be enabled");
        robot.clickOn(buttonFor(DEVICE_NAME, ".select-button"));
        waitForState(MainFormState.CONFIRMATION, Duration.ofSeconds(10));
        clickById("#forwardButton");
        assertTrue(waitUntil(() -> latestExecutionIdForDevice(deviceId) != null, Duration.ofSeconds(15)),
                "The execution should have been created at the backend");
        Integer id = latestExecutionIdForDevice(deviceId);
        assertNotNull(id);
        return id;
    }

    /**
     * Löst den {@link ClientDevice} über {@code ElwaManager#getManagedDevices()} auf - dieselbe
     * Objekt-Identität, die auch {@code ExecutionManager} verwendet (Identitäts-Cache, siehe
     * {@link ClientDevice} Klassenkommentar); funktioniert unverändert offline (über den
     * Snapshot, siehe {@code ElwaManager#getManagedDevices()}).
     */
    private static ClientDevice findManagedDeviceOffline(String name) throws Exception {
        for (ClientDevice d : ElwaManager.instance.getManagedDevices()) {
            if (name.equals(d.getName())) {
                return d;
            }
        }
        throw new IllegalStateException("Device '" + name + "' not found among the managed devices.");
    }

    private static Node buttonFor(String deviceName, String styleClass) {
        for (Node tile : primaryStage.getScene().getRoot().lookupAll(".device-list-item")) {
            if (containsText(tile, deviceName)) {
                return tile.lookup(styleClass);
            }
        }
        return null;
    }

    private static boolean isEnabled(Node n) {
        return n != null && !n.isDisabled();
    }

    private static boolean isVisible(Node n) {
        return n != null && n.isVisible();
    }

    private static boolean containsText(Node node, String text) {
        if (node instanceof javafx.scene.control.Labeled labeled && text.equals(labeled.getText())) {
            return true;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void clickById(String selector) {
        final Node node = robot.lookup(selector).query();
        robot.clickOn(node);
    }

    private static Boolean isFinishedInDb(int executionId) {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT finished FROM executions WHERE id=" + executionId)) {
            if (!r.next()) {
                return null;
            }
            return r.getBoolean(1);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer latestExecutionIdForDevice(int deviceId) {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery(
                        "SELECT id FROM executions WHERE device_id=" + deviceId + " ORDER BY id DESC LIMIT 1")) {
            if (!r.next()) {
                return null;
            }
            return r.getInt(1);
        } catch (Exception e) {
            return null;
        }
    }

    private static int executionCountForUser(int userId) {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery("SELECT COUNT(*) FROM executions WHERE user_id=" + userId)) {
            r.next();
            return r.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int creditAccountingCountForUser(int userId) {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement();
                ResultSet r = s.executeQuery(
                        "SELECT COUNT(*) FROM credit_accounting WHERE user_id=" + userId + " AND execution_id IS "
                                + "NOT NULL")) {
            r.next();
            return r.getInt(1);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void seedFixtures() throws Exception {
        cardIdA = "6" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);
        cardIdB = "7" + String.format("%08d", (System.currentTimeMillis() + 1) % 100_000_000L);
        cardIdC = "8" + String.format("%08d", (System.currentTimeMillis() + 2) % 100_000_000L);
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement()) {
            locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN " +
                    "(SELECT id FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%'))");
            s.executeUpdate("DELETE FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-%'");
            s.executeUpdate("DELETE FROM programs WHERE name LIKE 'E2E-%'");

            // Kein Auto-Ende, lange Maximaldauer: die Ausfuehrung bleibt fuer die Dauer des
            // Tests deterministisch "laufend", ohne mit dem 0W-Fallback des fhem-Simulators
            // zu wettlaufen (analog ClientResumeExecutionE2ETest).
            programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'FIXED', 3600, 0, 1.50, NULL, NULL, FALSE, 0, TRUE) RETURNING id");
            deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                            "fhem_power_name, deconz_uuid, auto_end_power_threshold, auto_end_wait_time, enabled) " +
                            "VALUES ('" + DEVICE_NAME + "', 1, " + locationId +
                            ", 'wm1', 'wm1sw', 'wm1pw', '', 0.5, 20, TRUE) RETURNING id");
            s.executeUpdate("INSERT INTO device_program_rel (device_id, program_id) VALUES ("
                    + deviceId + ", " + programId + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES ("
                    + programId + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                    + deviceId + ", " + groupId + ")");

            userIdA = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Offline A', 'e2e_off_a_" + System.currentTimeMillis() + "', '" + cardIdA + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userIdA + ", 100, 'E2E seed')");

            userIdB = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Offline B', 'e2e_off_b_" + System.currentTimeMillis() + "', '" + cardIdB + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userIdB + ", 100, 'E2E seed')");

            final int userIdC = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Offline C', 'e2e_off_c_" + System.currentTimeMillis() + "', '" + cardIdC + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userIdC + ", 100, 'E2E seed')");

            s.executeUpdate("UPDATE locations SET offline_max_duration_minutes=60 WHERE id=" + locationId);
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

    private static void waitForState(MainFormState target, Duration timeout) throws InterruptedException {
        assertTrue(waitUntil(() -> {
            final var c = ElwaManager.instance.getMainFormController();
            return c != null && c.getMainFormState() == target;
        }, timeout), "Expected to reach state " + target);
    }

    private static boolean waitUntil(BooleanSupplierEx condition, Duration timeout) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return condition.getAsBoolean();
    }

    @FunctionalInterface
    private interface BooleanSupplierEx {
        boolean getAsBoolean();
    }
}
