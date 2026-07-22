package org.kabieror.elwasys.raspiclient.offline;

import com.google.gson.Gson;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistentes, neustartfestes Ereignis-Journal für Start/Ende/Abbruch von Ausführungen
 * (Phase 4 AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am
 * Terminal" Punkt 3 "Persistentes Ereignis-Journal" und Auftrag "Stufe A - laufende
 * Executions lokal zu Ende führen"). Dasselbe Journal trägt BEIDE Stufen: laufende, online
 * gestartete Ausführungen, die nur beim Beenden nicht mehr an das Backend gemeldet werden
 * konnten (Stufe A), UND komplett offline gebuchte Ausführungen (Stufe B).
 * <p>
 * Format: eine JSON-Zeile je {@link OfflineJournalEntry} ({@code offline-journal.jsonl} im
 * Arbeitsverzeichnis, append-only) - Einträge bleiben in der Reihenfolge erhalten, in der sie
 * geschrieben wurden, und ein Absturz mitten im Schreiben eines einzelnen Eintrags kann
 * höchstens die LETZTE (unvollständige) Zeile beschädigen, alle vorherigen Zeilen bleiben
 * lesbar ({@link #readAll()} überspringt eine defekte letzte Zeile statt das gesamte Journal
 * zu verwerfen).
 * <p>
 * Bewusst UNVERSCHLÜSSELT (siehe {@link OfflineSnapshotStore} Klassenkommentar für die
 * Begründung/Auftraggeber-Entscheidung) - enthält aber ohnehin keine sicherheitsrelevanten
 * Daten (keine Passwörter, keine Kartennummern), nur Ids/Zeitstempel/Beträge.
 */
public class OfflineJournal {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path file;
    private final Gson gson;
    private final Object lock = new Object();

    public OfflineJournal(Path file) {
        this.file = file;
        this.gson = OfflineJsonSupport.gson();
    }

    /**
     * Hinterlegt den Start einer neu angelegten Ausführung (Stufe B: eine offline gebuchte
     * Ausführung, die noch keine echte Backend-Id hat).
     */
    public void appendStart(String idempotencyKey, LocalDateTime clientTimestamp, int userId, int deviceId,
            int programId) {
        append(new OfflineJournalEntry(OfflineJournalEntry.TYPE_START, idempotencyKey, clientTimestamp, userId,
                deviceId, programId, null, null, null));
    }

    /**
     * Hinterlegt das Ende/den Abbruch einer Ausführung. Genau eines von {@code executionId}
     * (Stufe A: die Ausführung lief bereits online, hat also eine echte Id) und
     * {@code startIdempotencyKey} (Stufe B: die Ausführung wurde selbst offline angelegt, die
     * echte Id ist erst nach dem Replay ihres eigenen {@code START}-Eintrags bekannt) ist
     * gesetzt.
     */
    public void appendFinish(boolean aborted, String idempotencyKey, LocalDateTime clientTimestamp, int userId,
            Integer executionId, String startIdempotencyKey, BigDecimal chargedPrice) {
        append(new OfflineJournalEntry(aborted ? OfflineJournalEntry.TYPE_ABORT : OfflineJournalEntry.TYPE_FINISH,
                idempotencyKey, clientTimestamp, userId, null, null, executionId, startIdempotencyKey, chargedPrice));
    }

    private void append(OfflineJournalEntry entry) {
        synchronized (this.lock) {
            try {
                Files.writeString(this.file, this.gson.toJson(entry) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            } catch (IOException e) {
                this.logger.error("Konnte Ereignis nicht im Offline-Journal ablegen - Ereignis '{}' (Schluessel "
                        + "'{}') droht verloren zu gehen!", entry.type(), entry.idempotencyKey(), e);
            }
        }
    }

    /**
     * Alle bislang noch nicht nachgemeldeten Einträge, in Schreibreihenfolge.
     */
    public List<OfflineJournalEntry> readAll() {
        synchronized (this.lock) {
            if (!Files.exists(this.file)) {
                return List.of();
            }
            List<OfflineJournalEntry> result = new ArrayList<>();
            try {
                List<String> lines = Files.readAllLines(this.file);
                for (String line : lines) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        result.add(this.gson.fromJson(line, OfflineJournalEntry.class));
                    } catch (Exception e) {
                        this.logger.warn(
                                "Konnte eine Zeile des Offline-Journals nicht lesen (evtl. durch einen Absturz "
                                        + "beschaedigt) - wird uebersprungen: {}", line, e);
                    }
                }
            } catch (IOException e) {
                this.logger.error("Konnte das Offline-Journal nicht lesen.", e);
            }
            return result;
        }
    }

    public boolean hasPendingEntries() {
        return !readAll().isEmpty();
    }

    /**
     * Entfernt einen einzelnen, noch nicht nachgemeldeten Eintrag anhand seines
     * Idempotenz-Schlüssels (z. B. eine offline angelegte Buchung, deren Steckdose sich
     * anschließend nicht einschalten ließ - siehe {@code executions.ExecutionManager
     * #resetOnFailure}: ein Replay dieses Journal-Eintrags würde sonst eine nie tatsächlich
     * genutzte "Geister-Ausführung" beim Backend anlegen).
     */
    public void removeEntry(String idempotencyKey) {
        synchronized (this.lock) {
            List<OfflineJournalEntry> remaining = new ArrayList<>();
            for (OfflineJournalEntry entry : readAll()) {
                if (!idempotencyKey.equals(entry.idempotencyKey())) {
                    remaining.add(entry);
                }
            }
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(this.file);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (OfflineJournalEntry entry : remaining) {
                    sb.append(this.gson.toJson(entry)).append(System.lineSeparator());
                }
                Files.writeString(this.file, sb.toString(), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException e) {
                this.logger.error("Konnte einen Eintrag (Schluessel '{}') nicht aus dem Offline-Journal entfernen.",
                        idempotencyKey, e);
            }
        }
    }

    /**
     * Leert das Journal vollständig - wird ausschließlich nach einem VOLLSTÄNDIG
     * erfolgreichen Replay ALLER Einträge aufgerufen (siehe {@code OfflineGateway#replay}).
     */
    public void clear() {
        synchronized (this.lock) {
            try {
                Files.deleteIfExists(this.file);
            } catch (IOException e) {
                this.logger.error("Konnte das abgearbeitete Offline-Journal nicht loeschen.", e);
            }
        }
    }

    /**
     * Summe der lokal berechneten Preise ({@link OfflineJournalEntry#chargedPrice()}) aller
     * noch nicht nachgemeldeten {@code FINISH}/{@code ABORT}-Einträge, je Benutzer - das vom
     * gecachten Snapshot-Guthaben abzuziehende, "lokal aufgelaufene" Offline-Guthaben-Delta
     * (Konzeptskizze Punkt 2 "lokal aufgelaufene Offline-Buchungen werden vom gecachten
     * Guthaben abgezogen"). Neustartfest, weil komplett aus dem persistenten Journal
     * hergeleitet - keine separate Ledger-Datei nötig.
     */
    public Map<Integer, BigDecimal> computeOfflineDebits() {
        Map<Integer, BigDecimal> result = new HashMap<>();
        for (OfflineJournalEntry entry : readAll()) {
            boolean isFinishOrAbort = OfflineJournalEntry.TYPE_FINISH.equals(entry.type())
                    || OfflineJournalEntry.TYPE_ABORT.equals(entry.type());
            if (isFinishOrAbort && entry.chargedPrice() != null && entry.userId() != null) {
                result.merge(entry.userId(), entry.chargedPrice(), BigDecimal::add);
            }
        }
        return result;
    }
}
