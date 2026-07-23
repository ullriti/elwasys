package org.kabieror.elwasys.raspiclient.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deterministische Tests für die Dead-Letter-Datenintegrität des Offline-Journals (Issue #69):
 * Ein fehlgeschlagener Dead-Letter-Schreibvorgang darf den Eintrag nicht spurlos verlieren
 * (write-before-remove), und ein dauerhaft defekter Datenträger darf keinen unendlichen
 * Busy-Loop erzeugen (neustartfester Fehlversuchszähler, endgültige Aufgabe nach N Versuchen).
 *
 * <p>Der Schreibfehler wird deterministisch erzwungen, indem an der Stelle der
 * Dead-Letter-Datei ein VERZEICHNIS angelegt wird - {@code Files.writeString} scheitert dann
 * mit einer {@link IOException}, ohne Sleeps/Zufall (siehe docs/kb/08-test-plan.md).
 */
class OfflineJournalTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    private static OfflineJournalEntry onlyEntryOf(OfflineJournal journal) {
        List<OfflineJournalEntry> entries = journal.readAll();
        return entries.get(0);
    }

    @Test
    void aFailedDeadLetterWriteKeepsTheEntryInTheActiveJournal(@TempDir Path dir) throws IOException {
        Path journalFile = dir.resolve("offline-journal.jsonl");
        OfflineJournal journal = new OfflineJournal(journalFile);
        journal.appendFinish(false, "f1", TS, 1, 99, null, new BigDecimal("1.00"));
        // Dead-Letter-Ziel mit einem Verzeichnis blockieren -> der Write scheitert.
        Files.createDirectory(dir.resolve("offline-journal.jsonl.deadletter"));

        journal.moveToDeadLetter(onlyEntryOf(journal), "erzwungener Schreibfehler");

        // Kein Totalverlust: der Eintrag ist WEDER im (nicht schreibbaren) Dead-Letter NOCH
        // verloren, sondern bleibt im aktiven Journal und wird beim nächsten Lauf erneut versucht.
        assertTrue(journal.hasPendingEntries(), "the entry survives a failed dead-letter write");
        assertEquals("f1", onlyEntryOf(journal).idempotencyKey());
    }

    @Test
    void anEntryIsFinallyGivenUpAfterTheMaximumNumberOfFailedDeadLetterWrites(@TempDir Path dir) throws IOException {
        Path journalFile = dir.resolve("offline-journal.jsonl");
        OfflineJournal journal = new OfflineJournal(journalFile);
        journal.appendFinish(false, "f1", TS, 1, 99, null, new BigDecimal("1.00"));
        Files.createDirectory(dir.resolve("offline-journal.jsonl.deadletter"));
        OfflineJournalEntry entry = onlyEntryOf(journal);

        // Die ersten (MAX - 1) Versuche lassen den Eintrag im Journal (kein Busy-Loop-Verlust).
        for (int i = 0; i < OfflineJournal.MAX_DEAD_LETTER_WRITE_ATTEMPTS - 1; i++) {
            journal.moveToDeadLetter(entry, "erzwungener Schreibfehler");
            assertTrue(journal.hasPendingEntries(), "still retried before reaching the attempt limit");
        }
        // Der MAX-te Versuch gibt den Eintrag endgültig auf, damit ein dauerhaft defekter
        // Datenträger den Replay nicht ewig verklemmt.
        journal.moveToDeadLetter(entry, "erzwungener Schreibfehler");
        assertFalse(journal.hasPendingEntries(), "the entry is finally given up after the attempt limit");
    }

    @Test
    void theFailureCounterSurvivesARestart(@TempDir Path dir) throws IOException {
        Path journalFile = dir.resolve("offline-journal.jsonl");
        OfflineJournal journal = new OfflineJournal(journalFile);
        journal.appendFinish(false, "f1", TS, 1, 99, null, new BigDecimal("1.00"));
        Files.createDirectory(dir.resolve("offline-journal.jsonl.deadletter"));

        // Ein fehlgeschlagener Versuch -> Zähler steht persistiert auf 1.
        journal.moveToDeadLetter(onlyEntryOf(journal), "erzwungener Schreibfehler");
        assertTrue(journal.hasPendingEntries());

        // Neustart simulieren: eine frische Instanz liest den persistierten Zähler.
        OfflineJournal restarted = new OfflineJournal(journalFile);
        OfflineJournalEntry entry = onlyEntryOf(restarted);
        // Wäre der Zähler rein In-Memory, bräuchte es jetzt wieder MAX Versuche; weil er
        // neustartfest bei 1 fortsetzt, genügen (MAX - 1) weitere bis zur endgültigen Aufgabe.
        for (int i = 0; i < OfflineJournal.MAX_DEAD_LETTER_WRITE_ATTEMPTS - 2; i++) {
            restarted.moveToDeadLetter(entry, "erzwungener Schreibfehler");
            assertTrue(restarted.hasPendingEntries(), "still retried before the persisted limit is reached");
        }
        restarted.moveToDeadLetter(entry, "erzwungener Schreibfehler");
        assertFalse(restarted.hasPendingEntries(),
                "the persisted counter continued across the restart, so the entry is given up");
    }
}
