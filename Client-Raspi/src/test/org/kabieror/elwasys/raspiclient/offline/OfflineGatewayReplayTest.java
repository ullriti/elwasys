package org.kabieror.elwasys.raspiclient.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kabieror.elwasys.raspiclient.api.ApiClient;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionDto;

/**
 * Deterministische Robustheitstests des Offline-Journal-Replays (Issue #17): Paar-Reihenfolge/
 * NPE-Absicherung, Dead-Letter für dauerhaft fachlich abgelehnte Einträge und der {@code
 * clear()}-Race. Statt eines echten Backends kommt ein {@link RecordingApiClient} zum Einsatz,
 * der {@link ApiClient} überschreibt und keine Netzwerkkommunikation macht - so bleibt der Test
 * ohne Sleeps/Zufall deterministisch (siehe docs/kb/06-ui-tests.md/08-test-plan.md).
 */
class OfflineGatewayReplayTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    private OfflineGateway newGateway(Path dir, ApiClient apiClient, OfflineJournal journal) {
        OfflineSnapshotStore snapshotStore = new OfflineSnapshotStore(dir.resolve("snapshot.json"));
        return new OfflineGateway(apiClient, snapshotStore, journal);
    }

    @Test
    void aStartIsReplayedOnlyOnceItsFinishIsAlsoJournaled(@TempDir Path dir) {
        // Kehrt das Backend mitten in einem offline gebuchten Waschgang zurück, darf der START
        // NICHT vorab (ohne sein Ende) nachgemeldet werden - sonst ginge die gelernte Backend-Id
        // verloren und das spätere FINISH liefe in eine NPE / meldete das Ende nie nach.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 1, 10, 100);
        RecordingApiClient api = new RecordingApiClient();
        api.startReplayResultId = 42;
        OfflineGateway gateway = newGateway(dir, api, journal);

        // 1. Lauf: Maschine läuft noch (kein FINISH im Journal) -> START wird übersprungen.
        boolean done = gateway.replay();
        assertFalse(done, "an in-progress START without its FINISH is not yet fully replayed");
        assertEquals(0, api.startReplays, "the START must not be replayed before its FINISH is journaled");
        assertTrue(journal.hasPendingEntries(), "the START stays in the journal until its FINISH arrives");

        // Maschine endet -> FINISH (Stufe B, referenziert den START-Schlüssel) wird journaliert.
        journal.appendFinish(false, "f1", TS.plusHours(1), 1, null, "s1", new BigDecimal("2.00"));

        // 2. Lauf: START und FINISH werden gemeinsam nachgemeldet, die Backend-Id wird korrekt
        // vom START zum FINISH durchgereicht (keine NPE).
        done = gateway.replay();
        assertTrue(done, "after the FINISH is journaled the pair replays completely");
        assertEquals(1, api.startReplays);
        assertEquals(1, api.finishes);
        assertEquals(42, api.lastFinishExecutionId, "the FINISH must use the id learned from the START replay");
        assertFalse(journal.hasPendingEntries(), "a fully replayed journal is empty");
    }

    @Test
    void aPoisonEntryIsMovedToDeadLetterAndValidEntriesStillReplay(@TempDir Path dir) throws IOException {
        // Ein dauerhaft fachlich abgelehnter START (z. B. gelöschtes Gerät -> 404) darf nicht das
        // ganze Journal verklemmen: er wird zur Dead-Letter, sein zugehöriges FINISH kann mangels
        // aufgelöster Id ebenfalls nicht nachgemeldet werden (Dead-Letter statt NPE), aber ein
        // unabhängiger, gültiger Stufe-A-Eintrag wird trotzdem nachgemeldet.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 1, 10, 100);
        journal.appendFinish(false, "f1", TS.plusHours(1), 1, null, "s1", new BigDecimal("2.00"));
        journal.appendFinish(false, "f2", TS, 2, 99, null, new BigDecimal("1.00")); // Stufe A, echte Id 99
        RecordingApiClient api = new RecordingApiClient();
        api.failStartReplayWith = new ApiException(404, "device-not-found", "Gerät unbekannt", "weg");
        OfflineGateway gateway = newGateway(dir, api, journal);

        boolean done = gateway.replay();

        assertTrue(done, "no communication failure occurred, so the run drains the journal");
        assertEquals(1, api.startReplays, "the poison START was attempted once");
        assertEquals(1, api.finishes, "only the independent, valid Stufe-A FINISH was replayed");
        assertEquals(99, api.lastFinishExecutionId);
        assertFalse(journal.hasPendingEntries(), "poison entries are removed (dead-lettered), valid ones replayed");

        Path deadLetter = dir.resolve("offline-journal.jsonl.deadletter");
        assertTrue(Files.exists(deadLetter), "a dead-letter file was written");
        List<String> deadLines = Files.readAllLines(deadLetter);
        assertEquals(2, deadLines.size(), "the poison START and its unresolvable FINISH were dead-lettered");
        assertTrue(deadLines.stream().anyMatch(l -> l.contains("s1")));
        assertTrue(deadLines.stream().anyMatch(l -> l.contains("f1")));
    }

    @Test
    void anEntryAppendedWhileReplayingIsNotLost(@TempDir Path dir) {
        // clear()-Race (Issue #17): endet eine Maschine WÄHREND des Replays, wird ihr frischer
        // FINISH-Eintrag angehängt. Er darf nicht mit dem abgearbeiteten Journal weggelöscht
        // werden - der Replay entfernt nur die tatsächlich nachgemeldeten Einträge einzeln.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendFinish(false, "f1", TS, 1, 1, null, new BigDecimal("1.00")); // Stufe A, Id 1
        RecordingApiClient api = new RecordingApiClient();
        // Simuliert das parallele Enden einer zweiten Maschine mitten im Replay.
        api.onFinish = () -> journal.appendFinish(false, "f2", TS, 2, 2, null, new BigDecimal("1.00"));
        OfflineGateway gateway = newGateway(dir, api, journal);

        boolean done = gateway.replay();

        assertFalse(done, "the entry appended during the run is still pending afterwards");
        assertEquals(1, api.finishes, "only the entry present at the start of the run was replayed");
        List<OfflineJournalEntry> remaining = journal.readAll();
        assertEquals(1, remaining.size(), "exactly the concurrently appended entry survives");
        assertEquals("f2", remaining.get(0).idempotencyKey(), "the fresh FINISH was not wiped by a blanket clear()");
    }

    @Test
    void anAbortIsReplayedAsAPairLikeAFinish(@TempDir Path dir) {
        // Wie die FINISH-Paar-Logik, aber mit einem ABORT als Terminierung - der ABORT nutzt
        // ebenfalls die beim START-Replay gelernte Backend-Id.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 1, 10, 100);
        journal.appendFinish(true, "a1", TS.plusHours(1), 1, null, "s1", new BigDecimal("2.00")); // aborted=true
        RecordingApiClient api = new RecordingApiClient();
        api.startReplayResultId = 42;
        OfflineGateway gateway = newGateway(dir, api, journal);

        boolean done = gateway.replay();

        assertTrue(done);
        assertEquals(1, api.startReplays);
        assertEquals(1, api.aborts, "the ABORT terminator was replayed via abortExecution");
        assertEquals(0, api.finishes);
        assertEquals(42, api.lastFinishExecutionId, "the ABORT must use the id learned from the START replay");
        assertFalse(journal.hasPendingEntries());
    }

    @Test
    void anUnknownEntryTypeIsDeadLetteredNotSilentlyDropped(@TempDir Path dir) throws IOException {
        // Ein Eintrag mit unbekanntem Typ (z. B. von einem neueren Client nach einem Downgrade)
        // darf nicht spurlos als "nachgemeldet" gelöscht werden, sondern muss in die
        // Dead-Letter-Datei wandern.
        Path journalFile = dir.resolve("offline-journal.jsonl");
        OfflineJournalEntry unknown = new OfflineJournalEntry("MYSTERY", "m1", TS, 1, 10, 100, null, null, null);
        Files.writeString(journalFile, OfflineJsonSupport.gson().toJson(unknown) + System.lineSeparator());
        OfflineJournal journal = new OfflineJournal(journalFile);
        RecordingApiClient api = new RecordingApiClient();
        OfflineGateway gateway = newGateway(dir, api, journal);

        boolean done = gateway.replay();

        assertTrue(done, "the journal is drained (the unknown entry is dead-lettered, not left pending)");
        assertEquals(0, api.startReplays);
        assertEquals(0, api.finishes);
        assertFalse(journal.hasPendingEntries(), "the unknown entry is removed from the active journal");
        Path deadLetter = dir.resolve("offline-journal.jsonl.deadletter");
        assertTrue(Files.exists(deadLetter), "the unknown entry left a dead-letter trace");
        assertTrue(Files.readAllLines(deadLetter).stream().anyMatch(l -> l.contains("m1")));
    }

    /**
     * Test-Doppel für {@link ApiClient}, das keine Netzwerkkommunikation macht, sondern die
     * Aufrufe protokolliert und konfigurierbar Ergebnisse/Fehler liefert.
     */
    private static final class RecordingApiClient extends ApiClient {

        int startReplays;
        int finishes;
        int aborts;
        int startReplayResultId = 1;
        Integer lastFinishExecutionId;
        ApiException failStartReplayWith;
        Runnable onFinish;

        RecordingApiClient() {
            super("http://localhost:1/", "test-token");
        }

        @Override
        public ExecutionDto replayCreateExecution(int userId, int deviceId, int programId,
                LocalDateTime clientTimestamp, String idempotencyKey) throws ApiException {
            this.startReplays++;
            if (this.failStartReplayWith != null) {
                throw this.failStartReplayWith;
            }
            return new ExecutionDto(this.startReplayResultId, deviceId, programId, userId, clientTimestamp, null,
                    false, null);
        }

        @Override
        public ExecutionDto createExecution(int userId, int deviceId, int programId, LocalDateTime clientTimestamp,
                String idempotencyKey) throws ApiException {
            throw new IllegalStateException("replay must use replayCreateExecution, not the live createExecution");
        }

        @Override
        public ExecutionDto finishExecution(int id, LocalDateTime clientTimestamp, String idempotencyKey)
                throws ApiException {
            this.finishes++;
            this.lastFinishExecutionId = id;
            if (this.onFinish != null) {
                this.onFinish.run();
            }
            return new ExecutionDto(id, 0, 0, 0, clientTimestamp, clientTimestamp, true, null);
        }

        @Override
        public ExecutionDto abortExecution(int id, LocalDateTime clientTimestamp, String idempotencyKey)
                throws ApiException {
            this.aborts++;
            this.lastFinishExecutionId = id;
            return new ExecutionDto(id, 0, 0, 0, clientTimestamp, clientTimestamp, true, null);
        }
    }
}
