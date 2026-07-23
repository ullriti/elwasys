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

    /**
     * Maximale Anzahl Versuche, einen Poison-Eintrag in die Dead-Letter-Datei zu schreiben,
     * bevor er endgültig aufgegeben wird (Issue #69). Solange das Schreiben scheitert (Platte
     * voll/defekt), bleibt der Eintrag im aktiven Journal (kein Totalverlust); nach so vielen
     * erfolglosen Versuchen wird er dennoch entfernt, damit ein dauerhaft defekter Datenträger
     * keinen unendlichen Busy-Loop (alle ~20s ein erneut scheiternder Backend-Aufruf + Write)
     * erzeugt.
     */
    static final int MAX_DEAD_LETTER_WRITE_ATTEMPTS = 5;

    private final Path file;
    private final Path deadLetterFile;
    private final Path deadLetterFailureFile;
    private final Gson gson;
    private final Object lock = new Object();

    /**
     * Neustartfester Fehlversuchszähler je Idempotenz-Schlüssel für fehlgeschlagene
     * Dead-Letter-Schreibvorgänge (Issue #69). Im Speicher gehalten (autoritativ für den
     * laufenden Prozess - bricht den Busy-Loop schon innerhalb einer Sitzung) UND best-effort
     * in {@link #deadLetterFailureFile} persistiert, damit ein Terminal-Neustart den Zähler
     * nicht auf 0 zurücksetzt (rein In-Memory würde einen Neustart nicht überstehen).
     */
    private final Map<String, Integer> deadLetterWriteFailures;

    public OfflineJournal(Path file) {
        this.file = file;
        this.deadLetterFile = file.resolveSibling(file.getFileName() + ".deadletter");
        this.deadLetterFailureFile = file.resolveSibling(file.getFileName() + ".deadletter-failures");
        this.gson = OfflineJsonSupport.gson();
        this.deadLetterWriteFailures = loadDeadLetterWriteFailures();
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
                // DSYNC erzwingt, dass der Journal-Eintrag vor der Rückkehr physisch auf den
                // Datenträger geschrieben ist. Ohne das könnte ein gerade journalierter
                // START/FINISH bei einem Stromausfall (Waschkeller ohne USV!) trotz "persistent"
                // verloren gehen - genau der Moment, den das Journal absichern soll (Issue #55).
                Files.writeString(this.file, this.gson.toJson(entry) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE,
                        StandardOpenOption.DSYNC);
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
    public boolean removeEntry(String idempotencyKey) {
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
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (OfflineJournalEntry entry : remaining) {
                    sb.append(this.gson.toJson(entry)).append(System.lineSeparator());
                }
                // DSYNC wie beim Anhängen (Issue #55): ein Stromausfall unmittelbar nach dem
                // Entfernen darf einen bereits nachgemeldeten/dead-letterten Eintrag nicht wieder
                // auferstehen lassen (Doppel-Nachmeldung; beim Backend zwar per Idempotenz-Key
                // abgefangen, aber vermeidbar).
                Files.writeString(this.file, sb.toString(), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
                return true;
            } catch (IOException e) {
                this.logger.error("Konnte einen Eintrag (Schluessel '{}') nicht aus dem Offline-Journal entfernen.",
                        idempotencyKey, e);
                return false;
            }
        }
    }

    /**
     * Verschiebt einen dauerhaft fachlich fehlgeschlagenen Eintrag (Poison-Entry, Issue #17)
     * aus dem aktiven Journal in eine separate Dead-Letter-Datei ({@code
     * offline-journal.jsonl.deadletter}) und entfernt ihn aus dem aktiven Journal. So verklemmt
     * ein einzelner, beim Backend niemals annehmbarer Eintrag (z. B. ein gelöschtes Gerät/
     * Programm, ein Ende ohne auflösbaren Start) nicht mehr das gesamte Journal: alle
     * nachfolgenden, gültigen Einträge (auch die anderer Nutzer und die Enden bereits online
     * gestarteter Waschgänge) werden weiterhin nachgemeldet. Die Dead-Letter-Datei bleibt für
     * eine spätere manuelle Sichtung/Diagnose erhalten (Grund je Zeile mitgeschrieben).
     */
    public void moveToDeadLetter(OfflineJournalEntry entry, String reason) {
        synchronized (this.lock) {
            String key = entry.idempotencyKey();
            if (this.deadLetterWriteFailures.getOrDefault(key, 0) >= MAX_DEAD_LETTER_WRITE_ATTEMPTS) {
                // Dieser Poison-Eintrag wurde bereits endgueltig aufgegeben (Datentraeger dauerhaft
                // defekt) - nicht erneut versuchen. Sonst schriebe jeder Replay-Lauf ein weiteres
                // Dead-Letter-Duplikat bzw. liefe in denselben scheiternden Write (Busy-Loop).
                return;
            }
            try {
                DeadLetterRecord record = new DeadLetterRecord(reason, LocalDateTime.now(), entry);
                // DSYNC (Issue #55): der Dead-Letter-Datensatz ist der einzige verbliebene Beleg
                // fuer diesen Eintrag und muss einen Stromausfall ueberstehen, bevor er unten aus
                // dem aktiven Journal entfernt wird.
                Files.writeString(this.deadLetterFile, this.gson.toJson(record) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE,
                        StandardOpenOption.DSYNC);
            } catch (IOException e) {
                // Write-before-Remove (Issue #69): das Dead-Letter-Schreiben scheiterte - der
                // Eintrag bleibt im aktiven Journal (kein Totalverlust), Versuch zaehlen.
                registerFailedDeadLetterOp(key, "in die Dead-Letter-Datei schreiben", e);
                return;
            }
            // Dead-Letter geschrieben - erst JETZT aus dem aktiven Journal entfernen. Schlaegt das
            // Entfernen fehl (Issue #69-Review-Fix: removeEntry verschluckt seine IOException
            // nicht mehr, sondern meldet false), NICHT als Erfolg werten: sonst wird der Eintrag
            // beim naechsten Lauf erneut nachgemeldet und ein weiteres Mal ins Dead-Letter
            // geschrieben (Duplikate + Busy-Loop), den der Zaehler sonst nie braeche.
            if (removeEntry(key)) {
                clearDeadLetterWriteFailure(key);
            } else {
                registerFailedDeadLetterOp(key, "nach dem Dead-Letter-Write aus dem aktiven Journal entfernen", null);
            }
        }
    }

    /**
     * Behandelt einen fehlgeschlagenen Teilschritt von {@link #moveToDeadLetter} (Issue #69):
     * zählt den Fehlversuch je Idempotenz-Schlüssel hoch (neustartfest) und gibt den Eintrag
     * nach {@link #MAX_DEAD_LETTER_WRITE_ATTEMPTS} endgültig auf, damit ein dauerhaft defekter
     * Datenträger keinen unendlichen Busy-Loop erzeugt. Der Aufruf läuft stets unter
     * {@link #lock}.
     */
    private void registerFailedDeadLetterOp(String key, String what, IOException cause) {
        int attempts = incrementDeadLetterWriteFailure(key);
        if (attempts >= MAX_DEAD_LETTER_WRITE_ATTEMPTS) {
            // Letzter Ausweg: den Eintrag dennoch aus dem aktiven Journal entfernen, damit er den
            // Replay nicht bei jedem Lauf erneut vergiftet. Der Verlust betrifft nur diesen einen,
            // ohnehin dauerhaft fachlich abgelehnten Eintrag und wird laut protokolliert. Gelingt
            // das Entfernen, ist der Zaehler obsolet; gelingt es nicht, bleibt er auf dem Limit,
            // sodass die Kurzschluss-Pruefung oben kuenftige Versuche (und Dead-Letter-Duplikate)
            // unterbindet.
            this.logger.error("Konnte einen Poison-Eintrag (Schluessel '{}') auch nach {} Versuchen nicht {} - er "
                    + "wird jetzt endgueltig aufgegeben (Datentraeger voll/defekt?).", key, attempts, what, cause);
            if (removeEntry(key)) {
                clearDeadLetterWriteFailure(key);
            }
        } else {
            this.logger.error("Konnte einen Poison-Eintrag (Schluessel '{}') nicht {} (Versuch {}/{}) - er bleibt im "
                    + "aktiven Journal und wird erneut versucht.", key, what, attempts, MAX_DEAD_LETTER_WRITE_ATTEMPTS,
                    cause);
        }
    }

    /**
     * Lädt den persistierten Dead-Letter-Fehlversuchszähler (Issue #69) - eine tolerante,
     * best-effort-Operation: fehlt oder ist die Datei beschädigt, wird mit einem leeren Zähler
     * begonnen (der In-Memory-Zähler bricht den Busy-Loop dann zumindest innerhalb der
     * laufenden Sitzung).
     */
    private Map<String, Integer> loadDeadLetterWriteFailures() {
        if (!Files.exists(this.deadLetterFailureFile)) {
            return new HashMap<>();
        }
        try {
            String json = Files.readString(this.deadLetterFailureFile);
            Map<String, Integer> loaded = this.gson.fromJson(json, DEAD_LETTER_FAILURE_MAP_TYPE);
            return loaded != null ? loaded : new HashMap<>();
        } catch (IOException | RuntimeException e) {
            this.logger.warn("Konnte den persistierten Dead-Letter-Fehlversuchszaehler nicht lesen - beginne mit "
                    + "leerem Zaehler.", e);
            return new HashMap<>();
        }
    }

    private static final java.lang.reflect.Type DEAD_LETTER_FAILURE_MAP_TYPE =
            com.google.gson.reflect.TypeToken.getParameterized(Map.class, String.class, Integer.class).getType();

    private int incrementDeadLetterWriteFailure(String idempotencyKey) {
        int attempts = this.deadLetterWriteFailures.getOrDefault(idempotencyKey, 0) + 1;
        this.deadLetterWriteFailures.put(idempotencyKey, attempts);
        persistDeadLetterWriteFailures();
        return attempts;
    }

    private void clearDeadLetterWriteFailure(String idempotencyKey) {
        if (this.deadLetterWriteFailures.remove(idempotencyKey) != null) {
            persistDeadLetterWriteFailures();
        }
    }

    /**
     * Persistiert den Fehlversuchszähler (Issue #69) - best effort. Schlägt selbst dieser
     * winzige Schreibvorgang fehl (derselbe defekte Datenträger), bleibt der autoritative
     * In-Memory-Zähler erhalten und bricht den Busy-Loop innerhalb der laufenden Sitzung; nur
     * ein Neustart würde ihn dann zurücksetzen.
     */
    private void persistDeadLetterWriteFailures() {
        try {
            if (this.deadLetterWriteFailures.isEmpty()) {
                Files.deleteIfExists(this.deadLetterFailureFile);
                return;
            }
            Files.writeString(this.deadLetterFailureFile, this.gson.toJson(this.deadLetterWriteFailures),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            this.logger.warn("Konnte den Dead-Letter-Fehlversuchszaehler nicht persistieren - er gilt nur fuer die "
                    + "laufende Sitzung.", e);
        }
    }

    /**
     * Ein in die Dead-Letter-Datei verschobener Journal-Eintrag samt Fehlergrund und
     * Zeitpunkt (Issue #17) - rein für die spätere Diagnose.
     */
    private record DeadLetterRecord(String reason, LocalDateTime deadLetteredAt, OfflineJournalEntry entry) {
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
