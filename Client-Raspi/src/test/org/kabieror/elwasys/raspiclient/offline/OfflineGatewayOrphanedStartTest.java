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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kabieror.elwasys.raspiclient.api.ApiClient;

/**
 * Regressionstest für den Befund aus dem Neustart-Test am Terminal Hilarenhaus (2026-09-20):
 * Eine rein lokale Offline-Ausführung überlebt einen Client-Neustart nicht, weil
 * {@code application.ElwaManager} laufende Ausführungen ausschließlich aus der
 * Geräteübersicht des BACKENDS wiederherstellt - eine noch nicht nachgemeldete Buchung hat
 * dort aber gar keine Nummer.
 * <p>
 * Der zurückbleibende START war damit <b>unsterblich</b>: {@link OfflineGateway#replay()}
 * überspringt einen START ohne Terminierung bewusst (Paar-Atomizität, Issue #80), er wird
 * also nie <em>versucht</em> - der eingebaute Fehlversuchszähler greift nicht, und eine
 * Alterung gibt es nicht. Am Gerät gemessen: alle 20 s ein folgenloser Replay-Durchlauf mit
 * "0 Eintraege nachgemeldet", unbegrenzt, und die Wäsche selbst nirgends verzeichnet.
 * <p>
 * Die Tests unterscheiden bewusst die drei Fälle, die gleich aussehen, aber verschieden
 * behandelt werden müssen - ein Test, der einfach alle übrig gebliebenen STARTs verwirft,
 * wäre grün und würde dabei laufende Wäschen wegräumen.
 */
class OfflineGatewayOrphanedStartTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 9, 20, 21, 38, 45);

    /** Sammelt die gemeldeten Vorfaelle, damit der Test die Sichtbarkeit mitpruefen kann. */
    private final List<OfflineIncident> reportedIncidents = new ArrayList<>();

    private OfflineGateway newGateway(Path dir, OfflineJournal journal) {
        OfflineSnapshotStore snapshotStore = new OfflineSnapshotStore(dir.resolve("snapshot.json"));
        // Der API-Client wird hier nie benutzt: die Aufloesung verwaister Eintraege laeuft
        // bewusst OHNE Backend-Aufruf (sie bucht ja gerade nicht nach).
        OfflineGateway gateway = new OfflineGateway(new ApiClient("http://localhost:1/", "t"),
                snapshotStore, journal);
        gateway.setIncidentReporter(this.reportedIncidents::add);
        return gateway;
    }

    @Test
    void anOrphanedStartIsMovedToTheDeadLetterFileInsteadOfLoopingForever(@TempDir Path dir) throws Exception {
        // Genau der Fall vom Gerät: Buchung auf Maschine 3 (Gerät 7) bei getrenntem Backend,
        // danach Client-Neustart - die Ausführung ist weg, ein Ende wurde nie journaliert.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("fa3d6fa0", TS, 35, 7, 2);
        OfflineGateway gateway = newGateway(dir, journal);

        // Beim Start läuft auf KEINEM Gerät mehr eine Ausführung.
        int resolved = gateway.resolveOrphanedStarts(Set.of());

        assertEquals(1, resolved, "der verwaiste START muss aufgeloest werden");
        assertFalse(journal.hasPendingEntries(),
                "nach der Aufloesung darf das Journal nicht mehr endlos denselben Eintrag anbieten");

        Path deadLetter = dir.resolve("offline-journal.jsonl.deadletter");
        assertTrue(Files.exists(deadLetter), "der Eintrag darf nicht still verschwinden");
        String content = Files.readString(deadLetter);
        assertTrue(content.contains("fa3d6fa0"),
                "der Vorgang muss nachvollziehbar bleiben - die Waesche ist sonst spurlos weg");

        // Die eigentliche Lehre aus dem Befund: nicht nur aufraeumen, sondern SICHTBAR machen.
        // Am Geraet war das Schlimmste nicht der liegengebliebene Eintrag, sondern dass
        // niemand erfahren haette, dass eine Buchung verloren ging.
        assertEquals(1, this.reportedIncidents.size(),
                "der Verlust muss als Vorfall gemeldet werden, nicht nur lokal weggeraeumt");
        assertEquals(OfflineIncident.KIND_DEAD_LETTER, this.reportedIncidents.get(0).kind());
    }

    @Test
    void aStartWhoseDeviceStillRunsIsKept(@TempDir Path dir) {
        // Der START wurde bereits nachgemeldet und wartet nur noch darauf, gemeinsam mit seiner
        // Terminierung zu verschwinden (Issue #80). Das Backend kennt die Ausführung, der
        // Client hat sie beim Start wiederhergestellt - hier darf NICHTS weggeräumt werden,
        // sonst verlöre das Gerät mitten im Waschgang seinen Eintrag.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 35, 7, 2);
        OfflineGateway gateway = newGateway(dir, journal);

        int resolved = gateway.resolveOrphanedStarts(Set.of(7));

        assertEquals(0, resolved, "ein START zu einem laufenden Geraet ist NICHT verwaist");
        assertTrue(journal.hasPendingEntries(), "der Eintrag muss im Journal bleiben");
        assertTrue(this.reportedIncidents.isEmpty(), "hier gibt es nichts zu melden");
    }

    @Test
    void aCompletePairIsLeftToTheRegularReplay(@TempDir Path dir) {
        // START + Terminierung liegen beide vor: das ist ein vollständiges Paar, das der
        // normale Replay nachmeldet. Es hier anzufassen, würde eine fertige Buchung
        // (inklusive Abrechnung) in die Dead-Letter-Datei umleiten.
        OfflineJournal journal = new OfflineJournal(dir.resolve("offline-journal.jsonl"));
        journal.appendStart("s1", TS, 35, 7, 2);
        journal.appendFinish(false, "f1", TS.plusMinutes(30), 35, null, "s1", new BigDecimal("1.20"));
        OfflineGateway gateway = newGateway(dir, journal);

        int resolved = gateway.resolveOrphanedStarts(Set.of());

        assertEquals(0, resolved, "ein vollstaendiges Paar gehoert dem regulaeren Replay");
        assertTrue(journal.hasPendingEntries());
    }
}
