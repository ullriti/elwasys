package org.kabieror.elwasys.raspiclient.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deterministische Tests der persistenten Vorfalls-Outbox (Issue #89): ein Vorfall entsteht
 * typischerweise genau dann, wenn die Verbindung zum Backend weg ist - die Meldung muss darum
 * einen Neustart überleben und erst nach der Quittung des Backends verschwinden. Ohne Netz und
 * ohne Sleeps: der Zustellweg ist ein einfacher, sammelnder {@link OfflineIncidentOutbox.Sender}.
 */
class OfflineIncidentOutboxTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    private static OfflineIncident incident(String key) {
        return OfflineIncident.of(OfflineIncident.KIND_DEAD_LETTER,
                new OfflineJournalEntry("FINISH", key, TS, 7, null, null, 77, null, new BigDecimal("1.50")),
                "FINISH " + key + ": abgelehnt");
    }

    @Test
    void anUndeliveredIncidentSurvivesARestartAndIsSentAgain(@TempDir Path dir) {
        Path file = dir.resolve("offline-incidents.jsonl");

        // 1. Terminal-Lauf: die Verbindung steht nicht (Sender meldet "nicht gesendet").
        RecordingSender offline = new RecordingSender(false);
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(file);
        outbox.setSender(offline);
        outbox.report(incident("f1"));

        // Die Zustellung laeuft bewusst asynchron auf einem eigenen Thread (der Replay-Thread ist
        // der Geldpfad und darf nie an einem blockierenden Send haengen) - hier auf sie warten,
        // statt die Zusicherung abzuschwaechen.
        assertTrue(outbox.awaitDeliveries(5000), "delivery thread finished");
        // At-least-once: ein zeitgleiches flush() (aus setSender) und die Einzelzustellung aus
        // report() koennen denselben Vorfall beide greifen. Das ist der zugesicherte Vertrag
        // (das Backend dedupliziert ueber den incidentKey) - entscheidend ist, DASS zugestellt
        // wurde, nicht wie oft.
        assertTrue(offline.sent.size() >= 1, "delivery was attempted right away");
        assertTrue(Files.exists(file), "the undelivered incident is persisted");

        // 2. Terminal-Lauf (Neustart): frische Outbox-Instanz auf derselben Datei, Verbindung da.
        RecordingSender online = new RecordingSender(true);
        OfflineIncidentOutbox afterRestart = new OfflineIncidentOutbox(file);
        assertEquals(1, afterRestart.readAll().size(), "the incident survived the restart");
        afterRestart.setSender(online);
        assertTrue(afterRestart.awaitDeliveries(5000), "delivery thread finished");

        assertEquals(1, online.sent.size(), "the incident is sent again once a connection exists");
        assertEquals("DEAD_LETTER:f1", online.sent.get(0).incidentKey(),
                "the retry carries the identical, deterministic incident key (server-side dedup)");
        assertEquals(1, afterRestart.readAll().size(), "sending alone does not remove it - only the ACK does");

        // Quittung des Backends (OFFLINE_INCIDENT_ACK).
        afterRestart.acknowledge("DEAD_LETTER:f1");
        assertTrue(afterRestart.readAll().isEmpty(), "after the ACK the incident is gone from the outbox");
        assertFalse(Files.exists(file), "an empty outbox leaves no file behind");
    }

    @Test
    void anAcknowledgementRemovesOnlyTheAcknowledgedIncident(@TempDir Path dir) {
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(dir.resolve("offline-incidents.jsonl"));
        RecordingSender sender = new RecordingSender(true);
        outbox.setSender(sender);
        outbox.report(incident("f1"));
        outbox.report(incident("f2"));

        outbox.acknowledge("DEAD_LETTER:f1");

        List<OfflineIncident> remaining = outbox.readAll();
        assertEquals(1, remaining.size());
        assertEquals("DEAD_LETTER:f2", remaining.get(0).incidentKey(),
                "an ACK must not drop incidents the backend has not confirmed");
    }

    @Test
    void theSameIncidentIsNotStoredTwice(@TempDir Path dir) {
        // Derselbe Vorfall kann erneut gemeldet werden (z. B. wenn der Dead-Letter-Write scheitert
        // und der Eintrag im Journal liegen bleibt) - die Outbox darf davon nicht anwachsen.
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(dir.resolve("offline-incidents.jsonl"));
        outbox.setSender(new RecordingSender(false));

        outbox.report(incident("f1"));
        outbox.report(incident("f1"));

        assertEquals(1, outbox.readAll().size(), "the deterministic incident key deduplicates the outbox");
    }

    @Test
    void reportingWithoutAnySenderStillPersists(@TempDir Path dir) {
        // Vor dem ersten Verbindungsaufbau ist noch kein Zustellweg verdrahtet - die Meldung darf
        // trotzdem nicht verloren gehen.
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(dir.resolve("offline-incidents.jsonl"));

        outbox.report(incident("f1"));

        assertEquals(1, outbox.readAll().size());
        // Den senderlosen Zustellversuch aus report() abwarten, BEVOR der Sender verdrahtet wird:
        // er liegt zu diesem Zeitpunkt nur in der Warteschlange des Zustell-Threads. Läuft er
        // erst nach setSender() an (ausgelasteter CI-Läufer), sendet er zusätzlich zum flush() -
        // der Test sah dann zwei Zustellungen statt einer. Im Betrieb ist diese Doppelung
        // harmlos (das Backend dedupliziert über den incidentKey), hier macht sie den Test flaky.
        assertTrue(outbox.awaitDeliveries(5000), "pending delivery attempt finished");

        RecordingSender sender = new RecordingSender(true);
        outbox.setSender(sender);
        assertTrue(outbox.awaitDeliveries(5000), "delivery thread finished");
        assertEquals(1, sender.sent.size(), "wiring the sender flushes what is pending");
    }

    @Test
    void aThrowingSenderKeepsTheIncidentInTheOutbox(@TempDir Path dir) {
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(dir.resolve("offline-incidents.jsonl"));
        outbox.setSender(i -> {
            throw new IllegalStateException("Zustellweg kaputt");
        });

        outbox.report(incident("f1"));

        assertEquals(1, outbox.readAll().size(), "a broken delivery path must not lose the incident");
    }

    /**
     * Zustellweg-Doppel: protokolliert die Sendeversuche und meldet konfigurierbar Erfolg
     * (Verbindung steht) oder Misserfolg (Verbindung weg).
     */
    private static final class RecordingSender implements OfflineIncidentOutbox.Sender {

        private final List<OfflineIncident> sent = new ArrayList<>();
        private final boolean connected;

        RecordingSender(boolean connected) {
            this.connected = connected;
        }

        @Override
        public boolean send(OfflineIncident incident) {
            this.sent.add(incident);
            return this.connected;
        }
    }

    @Test
    void afullOutboxKeepsTheOldestIncidentsAndDropsTheNewest(@TempDir Path dir) {
        // Ist die Outbox voll, ist die Lage ohnehin dauerhaft gestoert - dann sind die AELTESTEN
        // Meldungen (die Ursache) wertvoller als immer neue Folgemeldungen. Genau das sichert die
        // Klasse zu; ohne Test waere es nur ein Kommentar.
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(dir.resolve("offline-incidents.jsonl"));
        for (int i = 0; i < OfflineIncidentOutbox.MAX_OUTBOX_ENTRIES; i++) {
            outbox.report(incident("full-" + i));
        }
        assertEquals(OfflineIncidentOutbox.MAX_OUTBOX_ENTRIES, outbox.readAll().size(), "outbox is full");

        outbox.report(incident("one-too-many"));

        List<OfflineIncident> stored = outbox.readAll();
        assertEquals(OfflineIncidentOutbox.MAX_OUTBOX_ENTRIES, stored.size(), "the cap holds");
        assertEquals("DEAD_LETTER:full-0", stored.get(0).incidentKey(), "the oldest incident is kept");
        assertTrue(stored.stream().noneMatch(i -> i.incidentKey().contains("one-too-many")),
                "the newest incident is dropped, not an older one");
    }

    @Test
    void aCorruptedLineIsSkippedInsteadOfDiscardingTheWholeOutbox(@TempDir Path dir) throws Exception {
        // Ein Absturz mitten im Schreiben kann die letzte Zeile beschaedigen (derselbe Fall, den
        // OfflineJournal adressiert). Die uebrigen Meldungen - also die uebrigen Geldverluste -
        // muessen lesbar bleiben.
        Path file = dir.resolve("offline-incidents.jsonl");
        OfflineIncidentOutbox outbox = new OfflineIncidentOutbox(file);
        outbox.report(incident("intact"));
        Files.writeString(file, "{kaputt-abgeschnitten" + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);

        List<OfflineIncident> stored = outbox.readAll();

        assertEquals(1, stored.size(), "the intact incident survives a damaged line");
        assertEquals("DEAD_LETTER:intact", stored.get(0).incidentKey());
    }
}
