package org.kabieror.elwasys.raspiclient.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
 * Deterministische Tests der Vorfalls-Meldung aus dem Journal-Replay (Issue #89): ein
 * dead-gelettert Eintrag ist eine verlorene Offline-Buchung (Geld) und darf nicht nur im lokalen
 * Pi-Log stehen. Wie {@link OfflineGatewayReplayTest} ohne Netz/Sleeps, mit einem
 * {@link RecordingApiClient} als Test-Doppel und einem sammelnden
 * {@link OfflineIncidentReporter}.
 */
class OfflineGatewayIncidentReportTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    private OfflineGateway newGateway(Path dir, ApiClient apiClient, OfflineJournal journal,
            OfflineIncidentReporter reporter) {
        OfflineSnapshotStore snapshotStore = new OfflineSnapshotStore(dir.resolve("snapshot.json"));
        OfflineGateway gateway = new OfflineGateway(apiClient, snapshotStore, journal);
        gateway.setIncidentReporter(reporter);
        return gateway;
    }

    @Test
    void aDeadLetteredEntryIsReportedAsAnIncident(@TempDir Path dir) {
        // Ein fachlich abgelehnter Stufe-A-FINISH wandert in die Dead-Letter-Datei - der damit
        // verlorene Betrag muss als DEAD_LETTER-Vorfall gemeldet werden (bisher nur logger.error).
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendFinish(false, "f3", TS, 7, 77, null, new BigDecimal("1.50"));
        RecordingApiClient api = new RecordingApiClient();
        api.failFinishWith = new ApiException(409, "execution-already-finished", "Bereits beendet", "schon fertig");
        RecordingReporter reporter = new RecordingReporter();
        OfflineGateway gateway = newGateway(dir, api, journal, reporter);

        boolean done = gateway.replay();

        assertTrue(done, "the poison entry is dead-lettered, so the journal drains");
        assertEquals(1, reporter.incidents.size(), "exactly one incident is reported for one dead-lettered entry");
        OfflineIncident incident = reporter.incidents.get(0);
        assertEquals(OfflineIncident.KIND_DEAD_LETTER, incident.kind());
        assertEquals("DEAD_LETTER:f3", incident.incidentKey(), "the incident key is derived deterministically");
        assertEquals("FINISH", incident.entryType());
        assertEquals("f3", incident.idempotencyKey());
        assertEquals(Integer.valueOf(7), incident.userId());
        assertEquals(0, new BigDecimal("1.50").compareTo(incident.chargedPrice()),
                "the lost amount travels with the incident");
        assertEquals(TS, incident.occurredAt());
        assertTrue(incident.reason() != null && incident.reason().contains("f3"), "the dead-letter reason is passed on");
    }

    @Test
    void aFailedGhostAbortIsReportedAsAGhostExecutionIncident(@TempDir Path dir) {
        // Issue #68 + #89: der START wurde real angelegt (Id 42), sein FINISH scheitert fachlich
        // und auch der kompensierende Abort schlaegt fehl - die Execution bleibt serverseitig
        // "laufend". Genau dieser Fall braucht einen Alarm ausserhalb des Pi-Logs.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 1, 10, 100);
        journal.appendFinish(false, "f1", TS.plusHours(1), 1, null, "s1", new BigDecimal("2.00"));
        RecordingApiClient api = new RecordingApiClient();
        api.startReplayResultId = 42;
        api.failFinishWith = new ApiException(409, "execution-already-finished", "Bereits beendet", "schon fertig");
        api.failAbortWithRuntime = new IllegalStateException("Backend ist wieder weg");
        RecordingReporter reporter = new RecordingReporter();
        OfflineGateway gateway = newGateway(dir, api, journal, reporter);

        boolean done = gateway.replay();

        assertTrue(done);
        assertEquals(1, api.aborts, "the compensating abort was attempted and failed");
        assertEquals(1, reporter.countOfKind(OfflineIncident.KIND_GHOST_EXECUTION),
                "the unrecoverable ghost execution is reported");
        assertEquals(1, reporter.countOfKind(OfflineIncident.KIND_DEAD_LETTER),
                "the poison FINISH is additionally reported as a lost booking");
        OfflineIncident ghost = reporter.firstOfKind(OfflineIncident.KIND_GHOST_EXECUTION);
        assertEquals("GHOST_EXECUTION:f1", ghost.incidentKey(),
                "the ghost incident is keyed on the terminator that could not be replayed");
        assertTrue(ghost.reason().contains("42"), "the reason names the ghost execution id");
    }

    @Test
    void aSuccessfulGhostCompensationReportsNoGhostIncident(@TempDir Path dir) {
        // Gegenprobe: gelingt der kompensierende Abort, ist die Geister-Execution aufgeraeumt -
        // dann darf KEIN GHOST_EXECUTION-Vorfall gemeldet werden (nur der Dead-Letter des
        // Terminators selbst, denn dessen Buchung ist trotzdem verloren).
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 1, 10, 100);
        journal.appendFinish(false, "f1", TS.plusHours(1), 1, null, "s1", new BigDecimal("2.00"));
        RecordingApiClient api = new RecordingApiClient();
        api.startReplayResultId = 42;
        api.failFinishWith = new ApiException(409, "execution-already-finished", "Bereits beendet", "schon fertig");
        RecordingReporter reporter = new RecordingReporter();
        OfflineGateway gateway = newGateway(dir, api, journal, reporter);

        boolean done = gateway.replay();

        assertTrue(done);
        assertEquals(1, api.aborts, "the compensating abort succeeded");
        assertEquals(0, reporter.countOfKind(OfflineIncident.KIND_GHOST_EXECUTION),
                "a successful compensation is not an incident");
        assertEquals(1, reporter.countOfKind(OfflineIncident.KIND_DEAD_LETTER));
    }

    @Test
    void aThrowingReporterDoesNotAbortTheReplayRun(@TempDir Path dir) {
        // Fail-safe (Issue #89): der Replay ist der Geld-Pfad, die Meldung nur Diagnose. Wirft der
        // Melde-Weg, muss der Replay unveraendert weiterlaufen und den restlichen Eintrag
        // nachmelden.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendFinish(false, "f1", TS, 1, 11, null, new BigDecimal("1.00")); // Poison (Stufe A)
        journal.appendFinish(false, "f2", TS, 2, 22, null, new BigDecimal("1.00")); // gueltig
        RecordingApiClient api = new RecordingApiClient();
        api.failFinishOnceWith = new ApiException(409, "execution-already-finished", "Bereits beendet", "weg");
        OfflineGateway gateway = newGateway(dir, api, journal, incident -> {
            throw new IllegalStateException("Melde-Weg kaputt");
        });

        boolean done = gateway.replay();

        assertTrue(done, "the run completes despite the reporter throwing");
        assertEquals(2, api.finishes, "the valid entry after the poison one was still replayed");
        assertEquals(22, api.lastFinishExecutionId);
        assertFalse(journal.hasPendingEntries(), "the poison entry was still dead-lettered, the valid one replayed");
    }

    /**
     * Sammelnder {@link OfflineIncidentReporter} - hält die gemeldeten Vorfälle für die Prüfung.
     */
    private static final class RecordingReporter implements OfflineIncidentReporter {

        private final List<OfflineIncident> incidents = new ArrayList<>();

        @Override
        public void report(OfflineIncident incident) {
            this.incidents.add(incident);
        }

        long countOfKind(String kind) {
            return this.incidents.stream().filter(i -> kind.equals(i.kind())).count();
        }

        OfflineIncident firstOfKind(String kind) {
            return this.incidents.stream().filter(i -> kind.equals(i.kind())).findFirst().orElseThrow();
        }
    }

    /**
     * Test-Doppel für {@link ApiClient} ohne Netzwerkkommunikation (analog
     * {@code OfflineGatewayReplayTest.RecordingApiClient}).
     */
    private static final class RecordingApiClient extends ApiClient {

        int startReplays;
        int finishes;
        int aborts;
        int startReplayResultId = 1;
        Integer lastFinishExecutionId;
        ApiException failFinishWith;
        ApiException failFinishOnceWith;
        RuntimeException failAbortWithRuntime;

        RecordingApiClient() {
            super("http://localhost:1/", "test-token");
        }

        @Override
        public ExecutionDto replayCreateExecution(int userId, int deviceId, int programId,
                LocalDateTime clientTimestamp, String idempotencyKey) {
            this.startReplays++;
            return new ExecutionDto(this.startReplayResultId, deviceId, programId, userId, clientTimestamp, null,
                    false, null);
        }

        @Override
        public ExecutionDto finishExecution(int id, LocalDateTime clientTimestamp, String idempotencyKey)
                throws ApiException {
            this.finishes++;
            if (this.failFinishOnceWith != null) {
                ApiException failure = this.failFinishOnceWith;
                this.failFinishOnceWith = null;
                throw failure;
            }
            if (this.failFinishWith != null) {
                throw this.failFinishWith;
            }
            this.lastFinishExecutionId = id;
            return new ExecutionDto(id, 0, 0, 0, clientTimestamp, clientTimestamp, true, null);
        }

        @Override
        public ExecutionDto abortExecution(int id, LocalDateTime clientTimestamp, String idempotencyKey) {
            this.aborts++;
            if (this.failAbortWithRuntime != null) {
                throw this.failAbortWithRuntime;
            }
            return new ExecutionDto(id, 0, 0, 0, clientTimestamp, clientTimestamp, true, null);
        }
    }
}
