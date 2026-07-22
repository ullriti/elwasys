package org.kabieror.elwasys.raspiclient.model;

import org.kabieror.elwasys.raspiclient.api.dto.ExecutionDto;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Client-seitiges Gegenstück zu {@code Common.Execution} (Phase 4 AP4). Siehe
 * {@link ClientProgram} für die Begründung eigener, DB-freier Modellklassen. Die reinen
 * Zeit-/Anzeigeberechnungen ({@link #getRemainingTime()}, {@link #getElapsedTimeString()}, …)
 * sind 1:1 aus {@code Common.Execution} portiert (keine DB-Abhängigkeit, rein
 * feldbasiert) - die tatsächliche Persistenz (Start/Ende/Abrechnung) läuft jetzt über
 * {@code ApiClient}, aufgerufen von {@code ExecutionManager}/{@code ExecutionFinisher}.
 * <p>
 * <b>Virtuelle/lokale Ausführungen</b> ({@link #isVirtual()}): entsprechen
 * {@code Common.Execution#getOfflineExecution(...)}, verwendet für die "Tür öffnen"-Funktion
 * der Gerätekacheln. Genau wie im Alt-Code lösen sie NIE einen Server-Aufruf aus (siehe
 * {@code ExecutionManager}/{@code ExecutionFinisher}: dort wird {@link #isVirtual()}
 * geprüft, bevor {@code ApiClient#createExecution}/{@code #finishExecution}/
 * {@code #abortExecution}/{@code #resetExecution} aufgerufen wird) - Start/Ende werden nur
 * lokal vermerkt, es entsteht nie ein Datenbankeintrag und nie eine Abrechnung.
 *
 * <p><b>Offline gebuchte Ausführungen</b> ({@link #isOfflinePendingReplay()}, Phase 4 AP6,
 * siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"): anders als
 * eine virtuelle Ausführung sind das ECHTE, abzurechnende Buchungen, nur eben lokal
 * angelegt, während das Backend nicht erreichbar war ({@code offline.OfflineGateway}
 * #createExecution). Sie tragen noch KEINE echte Backend-Id ({@link #getId()} liefert einen
 * Platzhalter), sondern den beim Anlegen im Ereignis-Journal verwendeten Idempotenz-Schlüssel
 * ({@link #getOfflinePendingIdempotencyKey()}) - {@code ExecutionFinisher} referenziert damit
 * beim späteren Beenden/Abbrechen den zugehörigen Journal-Start-Eintrag, ohne einen (noch
 * nicht existierenden) Live-API-Aufruf zu versuchen; das Journal-Replay legt die Ausführung
 * beim Nachmelden dann tatsächlich serverseitig an.
 */
public class ClientExecution {

    private final int id;
    private final ClientDevice device;
    private final ClientProgram program;
    private final ClientUser user;
    private final boolean virtual;
    private final String offlinePendingIdempotencyKey;

    private LocalDateTime start;
    private LocalDateTime stop;
    private boolean finished;

    private ClientExecution(int id, ClientDevice device, ClientProgram program, ClientUser user, boolean virtual,
            String offlinePendingIdempotencyKey) {
        this.id = id;
        this.device = device;
        this.program = program;
        this.user = user;
        this.virtual = virtual;
        this.offlinePendingIdempotencyKey = offlinePendingIdempotencyKey;
    }

    /**
     * Baut eine {@link ClientExecution} aus einer frischen API-Antwort auf (nach
     * {@code POST /api/v1/executions}, dem Wiederaufnahme-Scan über
     * {@code GET /api/v1/executions/{id}}, oder nach finish/abort/reset).
     */
    public static ClientExecution of(ExecutionDto dto, ClientDevice device, ClientProgram program, ClientUser user) {
        ClientExecution e = new ClientExecution(dto.id(), device, program, user, false, null);
        e.applyDto(dto);
        return e;
    }

    /**
     * Entspricht {@code Common.Execution#getOfflineExecution(...)}: eine rein lokale
     * Ausführung ohne jede Serverkommunikation, für die "Tür öffnen"-Funktion.
     */
    public static ClientExecution offline(ClientDevice device, ClientProgram program, ClientUser user) {
        return new ClientExecution(-1, device, program, user, true, null);
    }

    /**
     * Eine WÄHREND eines Backend-Ausfalls neu gebuchte Ausführung (Phase 4 AP6, Stufe B,
     * siehe Klassenkommentar) - angelegt von {@code offline.OfflineGateway#createExecution},
     * nachdem Berechtigungs-/Guthabenprüfung lokal gegen den Snapshot erfolgreich waren und
     * ein START-Eintrag mit {@code startIdempotencyKey} im Ereignis-Journal hinterlegt wurde.
     *
     * @param localSequenceId nur für die Anzeige/Log-Zwecke; niemals an das Backend
     *                        geschickt (siehe {@link #getId()} Javadoc)
     */
    public static ClientExecution pendingOfflineReplay(int localSequenceId, ClientDevice device,
            ClientProgram program, ClientUser user, LocalDateTime start, String startIdempotencyKey) {
        ClientExecution e = new ClientExecution(-(1000 + Math.abs(localSequenceId)), device, program, user, false,
                startIdempotencyKey);
        e.start = start;
        return e;
    }

    /**
     * Ob dies eine rein lokale, nie abzurechnende "Tür öffnen"-Ausführung ist (siehe
     * Klassenkommentar). Bewusst NICHT (mehr) über {@code id < 0} bestimmt - eine
     * {@link #isOfflinePendingReplay() offline gebuchte} Ausführung hat ebenfalls eine
     * negative Platzhalter-Id, ist aber eine echte, abzurechnende Buchung.
     */
    public boolean isVirtual() {
        return this.virtual;
    }

    /**
     * Ob diese Ausführung während eines Backend-Ausfalls lokal gebucht wurde und noch nicht
     * per Journal-Replay beim Backend angelegt ist (siehe Klassenkommentar). Solange das der
     * Fall ist, hat {@link #getId()} keine reale Bedeutung - {@code ExecutionFinisher}
     * referenziert stattdessen {@link #getOfflinePendingIdempotencyKey()}.
     */
    public boolean isOfflinePendingReplay() {
        return this.offlinePendingIdempotencyKey != null;
    }

    /**
     * Der Idempotenz-Schlüssel, unter dem der START-Eintrag dieser Ausführung im
     * Ereignis-Journal liegt (siehe {@link #isOfflinePendingReplay()}), oder {@code null}
     * für jede online angelegte oder virtuelle Ausführung.
     */
    public String getOfflinePendingIdempotencyKey() {
        return this.offlinePendingIdempotencyKey;
    }

    public void applyDto(ExecutionDto dto) {
        this.start = dto.start();
        this.stop = dto.stop();
        this.finished = dto.finished();
    }

    /**
     * Die Backend-Id dieser Ausführung, oder ein negativer Platzhalter für eine
     * {@link #isVirtual() virtuelle} bzw. eine {@link #isOfflinePendingReplay() offline
     * gebuchte, noch nicht nachgemeldete} Ausführung (siehe Klassenkommentar) - im letzteren
     * Fall wird die echte Id erst durch das Journal-Replay vergeben, dieses Objekt selbst
     * erfährt sie nie (es referenziert stattdessen weiterhin
     * {@link #getOfflinePendingIdempotencyKey()}).
     */
    public int getId() {
        return this.id;
    }

    public ClientDevice getDevice() {
        return this.device;
    }

    public ClientProgram getProgram() {
        return this.program;
    }

    public ClientUser getUser() {
        return this.user;
    }

    public LocalDateTime getStartDate() {
        return this.start;
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#start()}: setzt die Startzeit nur beim
     * ersten Aufruf. Bei einer über {@link #of} aus der API angelegten Ausführung ist
     * {@code start} bereits gesetzt (das Backend startet eine Ausführung beim Anlegen sofort
     * mit, siehe {@code ExecutionController#start}) - dieser Aufruf ist dann ein No-Op.
     */
    public void start() {
        if (this.start != null) {
            return;
        }
        this.start = LocalDateTime.now();
    }

    /**
     * Rein lokales Vermerken des Endes, OHNE Serveraufruf - nur für virtuelle/offline
     * Ausführungen gedacht (siehe Klassenkommentar); reale Ausführungen werden über
     * {@code ExecutionFinisher} beendet, das stattdessen {@link #applyDto} mit der
     * API-Antwort von finish/abort aufruft.
     */
    public void stopLocally() {
        this.finished = true;
        this.stop = LocalDateTime.now();
    }

    /**
     * Rein lokales Zurücksetzen, OHNE Serveraufruf - nur für virtuelle/offline Ausführungen
     * (siehe Klassenkommentar); für reale Ausführungen ruft {@code ExecutionManager}
     * stattdessen {@code ApiClient#resetExecution} auf und verwirft dieses Objekt
     * anschließend ohnehin (siehe dort).
     */
    public void resetLocally() {
        this.start = null;
        this.stop = null;
        this.finished = false;
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#isRunning()}.
     */
    public boolean isRunning() {
        return !this.finished && this.start != null;
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#getEndDate()}.
     */
    public LocalDateTime getEndDate() {
        if (!this.finished) {
            return this.start.plus(this.program.getMaxDuration());
        } else {
            return this.stop;
        }
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#getRemainingTime()}.
     */
    public Duration getRemainingTime() {
        if (this.finished) {
            return Duration.ZERO;
        } else {
            return Duration.between(LocalDateTime.now(), this.getEndDate());
        }
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#getElapsedTime()}.
     */
    public Duration getElapsedTime() {
        if (this.start == null) {
            return Duration.ZERO;
        }
        if (this.finished) {
            if (this.getEndDate() == null) {
                return Duration.ZERO;
            }
            return Duration.between(this.getStartDate(), this.getEndDate());
        } else {
            return Duration.between(this.getStartDate(), LocalDateTime.now());
        }
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#getElapsedTimeString()}.
     */
    public String getElapsedTimeString() {
        long seconds = this.getElapsedTime().getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    /**
     * 1:1-Portierung von {@code Common.Execution#getEarliestAutoEnd()}.
     */
    public Duration getEarliestAutoEnd() {
        if (this.finished) {
            return Duration.ZERO;
        } else {
            final Duration earliest = this.program.getEarliestAutoEnd().minus(this.getElapsedTime());
            if (earliest.isNegative()) {
                return this.device.getAutoEndWaitTime();
            }
            return earliest.plus(this.device.getAutoEndWaitTime());
        }
    }
}
