package org.kabieror.elwasys.raspiclient.offline;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistente, neustartfeste Outbox für {@link OfflineIncident}-Meldungen (Issue #89) - nach
 * demselben Muster wie {@link OfflineJournal} (eine JSON-Zeile je Meldung, append-only, DSYNC).
 *
 * <p><b>Warum überhaupt persistent</b>: ein Dead-Letter/eine Geister-Execution entsteht
 * typischerweise GENAU DANN, wenn etwas kaputt ist - die WebSocket-Verbindung zum Backend kann in
 * genau diesem Moment weg sein. Eine reine Fire-and-Forget-Meldung ginge dann verloren und der
 * Alarm käme nie an. Die Meldung wird darum zuerst lokal abgelegt, bei bestehender bzw. neu
 * aufgebauter Verbindung gesendet ({@link #flush()}) und erst nach der Quittung des Backends
 * ({@code OFFLINE_INCIDENT_ACK} → {@link #acknowledge(String)}) wieder entfernt. Wiederholtes
 * Senden ist gefahrlos, weil das Backend über den {@link OfflineIncident#incidentKey()}
 * dedupliziert.
 *
 * <p><b>Begrenzungen</b> (analog {@link OfflineJournal#MAX_DEAD_LETTER_WRITE_ATTEMPTS}): die
 * Outbox fasst höchstens {@link #MAX_OUTBOX_ENTRIES} Meldungen und gibt das Schreiben nach
 * {@link #MAX_WRITE_ATTEMPTS} aufeinanderfolgenden Fehlversuchen auf - ein voller/defekter
 * Datenträger erzeugt so keinen Busy-Loop aus immer neuen, immer scheiternden Schreibvorgängen.
 * In beiden Fällen wird die Meldung wenigstens einmal direkt zugestellt versucht, und die
 * {@code logger.error}-Zeilen an den Vorfallsstellen bleiben ohnehin als Rückfallebene bestehen.
 */
public class OfflineIncidentOutbox implements OfflineIncidentReporter {

    /**
     * Maximale Anzahl unquittierter Meldungen in der Outbox. Ist sie erreicht, ist die Lage
     * ohnehin dauerhaft gestört - dann sind die ÄLTESTEN Meldungen (die Ursache) wertvoller als
     * immer neue Folgemeldungen, weshalb neue Meldungen verworfen statt alte verdrängt werden.
     */
    static final int MAX_OUTBOX_ENTRIES = 100;

    /**
     * Aufeinanderfolgende erfolglose Schreibversuche, nach denen die Outbox das Persistieren
     * aufgibt (Datenträger voll/defekt) - danach wird nur noch direkt zugestellt.
     */
    static final int MAX_WRITE_ATTEMPTS = 5;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path file;
    private final Gson gson;
    private final Object lock = new Object();

    /** Zustellweg, von außen verdrahtet (der WebSocket-Client) - {@code null} = kein Kanal. */
    private volatile Sender sender;

    /** Nur unter {@link #lock}: aufeinanderfolgende erfolglose Schreibversuche. */
    private int consecutiveWriteFailures;

    public OfflineIncidentOutbox(Path file) {
        this.file = file;
        this.gson = OfflineJsonSupport.gson();
    }

    /**
     * Verdrahtet den Zustellweg und stellt sofort alles Ausstehende zu - so wird die Outbox auch
     * dann geleert, wenn die Verbindung erst nach dem Vorfall zustande kommt.
     */
    public void setSender(Sender sender) {
        this.sender = sender;
        flush();
    }

    @Override
    public void report(OfflineIncident incident) {
        if (append(incident)) {
            flush();
        } else {
            // Nicht persistiert (Outbox voll bzw. Datentraeger defekt) - dann wenigstens EINMAL
            // direkt zustellen; ohne Outbox-Eintrag gibt es spaeter keine Wiederholung mehr.
            deliver(incident);
        }
    }

    /**
     * Stellt alle noch unquittierten Meldungen (erneut) zu - aufzurufen, sobald die Verbindung
     * zum Backend steht. Entfernt nichts: das passiert erst mit der Quittung des Backends in
     * {@link #acknowledge(String)}.
     */
    public void flush() {
        if (this.sender == null) {
            return;
        }
        for (OfflineIncident incident : readAll()) {
            if (!deliver(incident)) {
                // Die Leitung ist offenbar (wieder) weg - die restlichen Meldungen bleiben liegen
                // und werden beim naechsten Verbindungsaufbau erneut versucht.
                return;
            }
        }
    }

    /**
     * Entfernt eine vom Backend quittierte Meldung aus der Outbox (Antwort
     * {@code OFFLINE_INCIDENT_ACK}).
     */
    public void acknowledge(String incidentKey) {
        if (incidentKey == null) {
            return;
        }
        synchronized (this.lock) {
            List<OfflineIncident> remaining = new ArrayList<>();
            for (OfflineIncident incident : readAll()) {
                if (!incidentKey.equals(incident.incidentKey())) {
                    remaining.add(incident);
                }
            }
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(this.file);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (OfflineIncident incident : remaining) {
                    sb.append(this.gson.toJson(incident)).append(System.lineSeparator());
                }
                Files.writeString(this.file, sb.toString(), StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
            } catch (IOException e) {
                // Nicht schlimm: die Meldung wird beim naechsten Verbindungsaufbau erneut gesendet
                // und vom Backend ueber den incidentKey dedupliziert (kein Doppel-Eintrag).
                this.logger.warn("Konnte die quittierte Vorfallsmeldung '{}' nicht aus der Outbox entfernen.",
                        incidentKey, e);
            }
        }
    }

    /**
     * Die aktuell unquittierten Meldungen, in Schreibreihenfolge (Diagnose/Tests).
     */
    List<OfflineIncident> readAll() {
        synchronized (this.lock) {
            if (!Files.exists(this.file)) {
                return List.of();
            }
            List<OfflineIncident> result = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(this.file)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        OfflineIncident incident = this.gson.fromJson(line, OfflineIncident.class);
                        if (incident != null && incident.incidentKey() != null) {
                            result.add(incident);
                        }
                    } catch (RuntimeException e) {
                        this.logger.warn("Konnte eine Zeile der Vorfalls-Outbox nicht lesen (evtl. durch einen "
                                + "Absturz beschaedigt) - wird uebersprungen: {}", line, e);
                    }
                }
            } catch (IOException e) {
                this.logger.error("Konnte die Vorfalls-Outbox nicht lesen.", e);
            }
            return result;
        }
    }

    /**
     * Legt die Meldung in der Outbox ab.
     *
     * @return {@code true}, wenn die Meldung danach in der Outbox liegt (also erneut gesendet
     *         werden kann) - auch dann, wenn sie bereits vorher darin lag
     */
    private boolean append(OfflineIncident incident) {
        synchronized (this.lock) {
            List<OfflineIncident> existing = readAll();
            for (OfflineIncident known : existing) {
                if (incident.incidentKey().equals(known.incidentKey())) {
                    // Derselbe Vorfall liegt schon unquittiert in der Outbox - kein zweiter
                    // Eintrag; die Wiederholung uebernimmt flush().
                    return true;
                }
            }
            if (existing.size() >= MAX_OUTBOX_ENTRIES) {
                this.logger.error("Vorfalls-Outbox ist voll ({} unquittierte Meldungen) - die Meldung '{}' wird nur "
                        + "einmalig zugestellt versucht und nicht aufbewahrt.", existing.size(),
                        incident.incidentKey());
                return false;
            }
            if (this.consecutiveWriteFailures >= MAX_WRITE_ATTEMPTS) {
                // Schreiben ist dauerhaft gestoert - nicht bei jedem Vorfall erneut versuchen
                // (Busy-Loop auf einem defekten Datentraeger).
                return false;
            }
            try {
                // DSYNC wie im Journal (Issue #55): die Meldung soll gerade den Fall ueberstehen,
                // in dem das Terminal unmittelbar danach ausfaellt.
                Files.writeString(this.file, this.gson.toJson(incident) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE,
                        StandardOpenOption.DSYNC);
                this.consecutiveWriteFailures = 0;
                return true;
            } catch (IOException e) {
                this.consecutiveWriteFailures++;
                this.logger.error("Konnte die Vorfallsmeldung '{}' nicht in der Outbox ablegen (Versuch {}/{}).",
                        incident.incidentKey(), this.consecutiveWriteFailures, MAX_WRITE_ATTEMPTS, e);
                return false;
            }
        }
    }

    /**
     * Einzelne Zustellung - best effort: ein Fehler im Zustellweg darf niemals nach außen dringen
     * (der Aufrufer ist mittelbar der Journal-Replay, siehe {@link OfflineIncidentReporter}).
     *
     * @return {@code true}, wenn die Meldung auf die Leitung gegeben werden konnte
     */
    private boolean deliver(OfflineIncident incident) {
        Sender currentSender = this.sender;
        if (currentSender == null) {
            return false;
        }
        try {
            return currentSender.send(incident);
        } catch (RuntimeException e) {
            this.logger.warn("Konnte die Vorfallsmeldung '{}' nicht an das Backend senden - sie bleibt in der Outbox "
                    + "und wird beim naechsten Verbindungsaufbau erneut versucht.", incident.incidentKey(), e);
            return false;
        }
    }

    /**
     * Zustellweg der Outbox (vom {@code ws.TerminalWebSocketClient} implementiert).
     */
    @FunctionalInterface
    public interface Sender {

        /**
         * @return {@code true}, wenn die Meldung tatsächlich gesendet wurde (steht die Verbindung
         *         gerade nicht, {@code false} - dann bleibt die Meldung in der Outbox)
         */
        boolean send(OfflineIncident incident);
    }
}
