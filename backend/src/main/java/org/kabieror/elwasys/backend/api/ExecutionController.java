package org.kabieror.elwasys.backend.api;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.kabieror.elwasys.backend.api.dto.ExecutionDto;
import org.kabieror.elwasys.backend.api.dto.ExecutionEndRequest;
import org.kabieror.elwasys.backend.api.dto.ExecutionStartRequest;
import org.kabieror.elwasys.backend.api.exception.DeviceNotUsableException;
import org.kabieror.elwasys.backend.api.exception.DeviceOccupiedException;
import org.kabieror.elwasys.backend.api.exception.ExecutionAlreadyFinishedException;
import org.kabieror.elwasys.backend.api.exception.InsufficientCreditException;
import org.kabieror.elwasys.backend.api.exception.LocationNotAllowedException;
import org.kabieror.elwasys.backend.api.exception.ProgramNotAvailableException;
import org.kabieror.elwasys.backend.api.exception.ProgramNotFoundException;
import org.kabieror.elwasys.backend.api.exception.UserBlockedException;
import org.kabieror.elwasys.backend.api.exception.UserNotFoundException;
import org.kabieror.elwasys.backend.api.idempotency.IdempotencyService;
import org.kabieror.elwasys.backend.api.idempotency.IdempotentResult;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.notification.ExecutionNotificationEvent;
import org.kabieror.elwasys.backend.offline.ClientTimestampPolicy;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.AdvisoryLockService;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Execution-Lebenszyklus über die Terminal-API (AP4, siehe docs/kb/05-migration-plan.md):
 * starten/beenden/abbrechen/zurücksetzen, jeweils über {@link ExecutionService}/
 * {@link CreditService} (1:1-Portierung der Alt-Code-Fachregeln aus AP2, inkl. Abrechnung).
 *
 * <p>Persistenzseitig entspricht {@link #start} der Kombination aus
 * {@code DataManager#newExecution} + {@code ExecutionManager#startExecution} (die
 * hardwarenahe Steckdosenansteuerung bleibt im Terminal - das Terminal ruft diesen Endpunkt
 * unmittelbar davor/danach auf). {@link #reset} entspricht {@code Execution#reset()}, das der
 * Alt-Client nur aufruft, wenn das Einschalten der Steckdose NACH dem Anlegen der Ausführung
 * fehlschlägt (siehe {@code ExecutionManager#startExecution}, catch-Block).
 *
 * <p>Standort-Scope strikt durchgesetzt über {@link TerminalScopeGuard}.
 *
 * <p><b>Idempotenz (AP3, Phase 4, additiv)</b>: alle vier Endpunkte akzeptieren einen
 * optionalen {@code Idempotency-Key}-Header (eine vom Terminal erzeugte UUID pro fachlichem
 * Ereignis). Wird derselbe Schlüssel erneut gesendet (z.B. nach einem Verbindungsabbruch vor
 * Erhalt der ursprünglichen Antwort), liefert {@link IdempotencyService} die zuerst
 * berechnete Antwort erneut aus, OHNE die fachliche Aktion (Abrechnung, Benachrichtigung)
 * ein zweites Mal auszulösen. Fehlt der Header, verhalten sich die Endpunkte exakt wie vor
 * AP3 (siehe {@link IdempotencyService} Klassen-Javadoc).
 *
 * <p><b>Original-Zeitstempel (AP3, Phase 4, additiv)</b>: {@link ExecutionStartRequest#clientTimestamp()}
 * bzw. {@link ExecutionEndRequest#clientTimestamp()} lassen das Terminal den tatsächlichen
 * Ereigniszeitpunkt mitschicken (statt der Serverzeit beim Empfang) - Vorbereitung für die
 * Offline-Nachmeldung aus AP6 (siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen
 * am Terminal"). Fehlt das Feld, verwendet der Server wie bisher seine eigene Uhr.
 *
 * <p><b>Benachrichtigungen (AP3, Phase 4)</b>: ein reguläres Ende ({@link #finish}) bzw. ein
 * Abbruch ({@link #abort}) publiziert ein
 * {@link org.kabieror.elwasys.backend.notification.ExecutionNotificationEvent} - 1:1 am selben
 * Auslösepunkt wie {@code ExecutionFinisher#executeAction()} im Client-Alt-Code. Der Versand
 * selbst läuft erst nach dem Commit der Finish-Transaktion im
 * {@link org.kabieror.elwasys.backend.notification.ExecutionNotificationListener} (Issue #36,
 * {@code @TransactionalEventListener} / {@code AFTER_COMMIT}), damit ein hängender SMTP-Server
 * nicht die DB-Transaktion blockiert und eine zurückgerollte Transaktion keine "fertig"-Mail
 * zu einer nicht verbuchten Ausführung auslöst. Das Gating hinter
 * {@code elwasys.notifications.enabled} (Default AUS) verbleibt vollständig im
 * {@code NotificationService}. Bei einem idempotenten Replay (siehe oben) wird das Ereignis
 * NICHT erneut publiziert, weil die fachliche Aktion dann gar nicht erst erneut ausgeführt
 * wird.
 *
 * <p><b>Offline-Nachmeldung/Zeitstempel-Toleranz (AP6, Phase 4, additiv)</b>: ein
 * {@code clientTimestamp}, der außerhalb des erlaubten Zeitfensters (Standort-
 * {@code offline.max-duration} + Uhren-Drift-Toleranz) liegt, wird von
 * {@link ClientTimestampPolicy#resolve} durch die Serverzeit ersetzt statt die Anfrage
 * abzulehnen (Auftraggeber-Vorgabe: "Server-Zeit + Protokollhinweis"). Zusätzlich wird eine
 * Benachrichtigung zu einem {@code finish}/{@code abort} unterdrückt, wenn das Ereignis
 * selbst älter als {@code offline.max-duration} ist (siehe
 * {@link ClientTimestampPolicy#isNotificationSuppressed}) - beides bewusst NACH der
 * Idempotenz-Prüfung ausgeführt, da ein Replay ohnehin nichts erneut auslöst.
 *
 * <p><b>Privilegierter Nachbuchungs-Pfad (Issue #16)</b>: kennzeichnet der Aufrufer eine
 * {@code start}-Meldung über {@link ExecutionStartRequest#replay()} als Offline-Nachmeldung,
 * überspringt {@link #start} die fachlichen Wächter (Sperrung/Standort/Nutzbarkeit/Belegung/
 * Guthaben). Andernfalls würde ein fachlich abgelehnter Nachbuchungs-Eintrag bei JEDEM Versuch
 * erneut scheitern und - weil der Idempotenz-Schlüssel nur bei Erfolg abgelegt wird (siehe
 * {@link IdempotencyService}) - das gesamte Terminal-Journal dauerhaft verklemmen. Eine
 * Nachmeldung ist ein Fakt, keine Anfrage; die Auftraggeber-Festlegung (siehe
 * docs/kb/05-migration-plan.md und ADR 0010) lässt beim Replay ausdrücklich auch negative
 * Salden zu. Live-Buchungen (ohne Replay-Flag) durchlaufen die Wächter unverändert.
 */
@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ProgramRepository programRepository;

    private final UserRepository userRepository;

    private final PermissionService permissionService;

    private final PricingService pricingService;

    private final CreditService creditService;

    private final ExecutionService executionService;

    private final TerminalScopeGuard scopeGuard;

    private final IdempotencyService idempotencyService;

    private final ClientTimestampPolicy clientTimestampPolicy;

    private final AdvisoryLockService advisoryLockService;

    private final ApplicationEventPublisher eventPublisher;

    public ExecutionController(ProgramRepository programRepository, UserRepository userRepository,
            PermissionService permissionService, PricingService pricingService, CreditService creditService,
            ExecutionService executionService, TerminalScopeGuard scopeGuard, IdempotencyService idempotencyService,
            ClientTimestampPolicy clientTimestampPolicy, AdvisoryLockService advisoryLockService,
            ApplicationEventPublisher eventPublisher) {
        this.programRepository = programRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.pricingService = pricingService;
        this.creditService = creditService;
        this.executionService = executionService;
        this.scopeGuard = scopeGuard;
        this.idempotencyService = idempotencyService;
        this.clientTimestampPolicy = clientTimestampPolicy;
        this.advisoryLockService = advisoryLockService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionDto start(@AuthenticationPrincipal TerminalPrincipal terminal,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ExecutionStartRequest request) {
        // Nur device wird AUSSERHALB des Idempotenz-Zweigs aufgelöst: idempotencyService.execute
        // braucht dessen Standort für den Scope-Parameter, und der Standort-Scope-Wächter ist
        // zugleich die Authentifizierung dieses Aufrufs. program/user werden dagegen bewusst
        // INNERHALB des Zweigs aufgelöst (Issue #41): ein legitimer Replay soll die gespeicherte
        // Antwort auch dann liefern, wenn user/program zwischen Erst- und Wiederholungsaufruf
        // gelöscht wurden - würden sie vorab aufgelöst, scheiterte ein solcher Replay fälschlich
        // mit 404 statt der gespeicherten 201.
        DeviceEntity device = this.scopeGuard.requireDeviceInScope(request.deviceId(), terminal);
        boolean replay = request.isReplay();

        IdempotentResult<ExecutionDto> result = this.idempotencyService.execute(idempotencyKey, device.getLocation(),
                "execution-start", HttpStatus.CREATED.value(), ExecutionDto.class, () -> {
                    ProgramEntity program = this.programRepository.findById(request.programId()).orElseThrow(
                            () -> new ProgramNotFoundException(request.programId()));
                    // Offline-Nachmeldung (Issue #16): eine bereits offline gebuchte Ausführung
                    // wird nachgetragen - das Ereignis ist ein FAKT, keine Anfrage. Die
                    // fachlichen Wächter würden hier einen zwischenzeitlich geänderten Zustand
                    // (Sperrung, standortübergreifende Guthabenänderung, ein zweiter
                    // Offline-Start, dessen Reservierung noch nicht gebucht ist) fälschlich als
                    // Ablehnung werten und den kompletten Journal-Replay dauerhaft verklemmen.
                    // Auftraggeber-Festlegung (docs/kb/05-migration-plan.md, ADR 0010): der
                    // Snapshot-Stand gilt, negativ gewordene Salden werden normal verbucht.
                    UserEntity user;
                    if (!replay) {
                        // Issue #20: Belegungs- und Guthabenentscheidung serialisieren, damit
                        // zwei parallele Starts nicht beide ein freies Gerät bzw. ausreichendes
                        // Guthaben sehen und doppelt belegen/reservieren. Reihenfolge bewusst
                        // erst Gerät (Advisory-Lock), dann Nutzer (Zeilensperre) - konsistent zu
                        // den übrigen Geldpfaden, um Deadlocks auszuschließen.
                        this.advisoryLockService.lockDevice(device.getId());
                        // Nutzer FRISCH und pessimistisch GESPERRT laden (nicht vorab per
                        // findById): so entscheiden die Wächter (isBlocked/Rechte) und der
                        // Guthabencheck auf dem Stand NACH Lock-Erwerb, nicht auf einem davor
                        // gelesenen Snapshot.
                        user = this.userRepository.findWithLockById(request.userId()).orElseThrow(
                                () -> new UserNotFoundException(request.userId()));
                        if (user.isBlocked()) {
                            throw new UserBlockedException(user.getId());
                        }
                        if (!this.permissionService.isUserAllowedAtLocation(user, device.getLocation())) {
                            throw new LocationNotAllowedException(user.getId(), device.getLocation().getName());
                        }
                        if (!this.permissionService.isDeviceUsableByUser(device, user)) {
                            throw new DeviceNotUsableException(device.getId(), user.getId());
                        }
                        if (!this.permissionService.isProgramAvailableForDeviceAndUser(device, program, user)) {
                            throw new ProgramNotAvailableException(program.getId(), device.getId(), user.getId());
                        }
                        if (this.executionService.getRunningExecution(device).isPresent()) {
                            throw new DeviceOccupiedException(device.getId());
                        }
                        BigDecimal maxPrice = this.pricingService.getPrice(program,
                                Duration.ofSeconds(program.getMaxDurationSeconds()), user);
                        if (!this.creditService.canAfford(user, maxPrice)) {
                            throw new InsufficientCreditException(user.getId(), maxPrice,
                                    this.creditService.getCredit(user));
                        }
                    } else {
                        // Replay: keine fachlichen Wächter, kein Lock nötig (die Nachmeldung ist
                        // ein Fakt) - der Nutzer wird nur zum Anlegen der Ausführung benötigt.
                        // Defense-in-Depth (Issue #67): das replay-Flag umgeht ALLE Wächter und
                        // ist rein client-gesteuert; ohne Korrelation zu einer echten vorherigen
                        // Offline-Buchung. Als Härtung wird ein plausibel in der Vergangenheit
                        // liegender Original-Zeitstempel VERLANGT (ein Replay ohne/mit "jetzt"-
                        // Zeitstempel ist verdächtig) und jede privilegierte Nachbuchung
                        // auditiert, damit anomale Muster sichtbar werden. Ein abgelehnter Replay
                        // (422) wandert beim Terminal ins Dead-Letter statt das Journal zu
                        // verklemmen (Issue #17).
                        this.clientTimestampPolicy.requireValidReplayTimestamp(request.clientTimestamp(),
                                device.getLocation());
                        user = this.userRepository.findById(request.userId()).orElseThrow(
                                () -> new UserNotFoundException(request.userId()));
                        this.logger.info(
                                "Privilegierte Offline-Nachbuchung (Replay) angenommen: Nutzer {}, Gerät {} ('{}'), "
                                        + "Programm {}, Original-Zeitstempel {}, Standort '{}' - fachliche Wächter "
                                        + "übersprungen (Issue #16), Audit (Issue #67).", user.getId(), device.getId(),
                                device.getName(), program.getId(), request.clientTimestamp(),
                                device.getLocation().getName());
                    }
                    ExecutionEntity execution = this.executionService.createExecution(device, program, user);
                    LocalDateTime resolvedTimestamp = this.clientTimestampPolicy.resolve(request.clientTimestamp(),
                            device.getLocation(), "execution-start");
                    execution = this.executionService.startExecution(execution, resolvedTimestamp);
                    return toDto(execution);
                });
        return result.body();
    }

    @GetMapping("/{id}")
    public ExecutionDto get(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id) {
        ExecutionEntity execution = this.scopeGuard.requireExecutionInScope(id, terminal);
        return toDto(execution);
    }

    /**
     * Reguläres Ende einer Programmausführung (Auto-Ende oder manuelles Beenden durch den
     * Benutzer nach Programmablauf) - entspricht dem persistenzseitigen Teil von
     * {@code ExecutionFinisher#executeAction()}.
     */
    @PostMapping("/{id}/finish")
    public ExecutionDto finish(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ExecutionEndRequest request) {
        return finishOrAbort(terminal, id, idempotencyKey, request, "execution-finish", false);
    }

    /**
     * Vorzeitiger Abbruch durch den Benutzer. Persistenzseitig identisch zu {@link #finish}
     * (siehe {@link ExecutionService#finishExecution} Javadoc) - eigener Endpunkt für eine
     * klare API-Semantik (u.a. der Abbruch-Benachrichtigungstext, siehe Klassen-Javadoc
     * "Benachrichtigungen") und künftige Erweiterbarkeit.
     */
    @PostMapping("/{id}/abort")
    public ExecutionDto abort(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) ExecutionEndRequest request) {
        return finishOrAbort(terminal, id, idempotencyKey, request, "execution-abort", true);
    }

    private ExecutionDto finishOrAbort(TerminalPrincipal terminal, Integer id, String idempotencyKey,
            ExecutionEndRequest request, String operation, boolean aborted) {
        ExecutionEntity execution = this.scopeGuard.requireExecutionInScope(id, terminal);
        LocalDateTime clientTimestamp = request == null ? null : request.clientTimestamp();
        LocationEntity location = execution.getDevice().getLocation();

        // Der "bereits beendet"-Wächter muss INNERHALB des Idempotenz-Zweigs geprüft werden
        // (siehe IdempotencyService Javadoc): bei einem Replay ist die Ausführung durch den
        // ERSTEN Aufruf bereits finished=true - ein Check VOR dem Idempotenz-Lookup würde
        // einen Replay fälschlich mit 409 statt der gespeicherten 200-Antwort beenden.
        IdempotentResult<ExecutionDto> result = this.idempotencyService.execute(idempotencyKey, location, operation,
                HttpStatus.OK.value(), ExecutionDto.class, () -> {
                    // Issue #20: Ausführung FRISCH und pessimistisch GESPERRT laden statt die
                    // zuvor (für den Standort-Scope) detacht geladene Instanz zu prüfen - so
                    // durchlaufen zwei parallele finish-Aufrufe nicht beide den
                    // finished=false-Zweig und buchen doppelt ab.
                    ExecutionEntity locked = this.executionService.getForUpdate(id);
                    if (locked.isFinished()) {
                        throw new ExecutionAlreadyFinishedException(id);
                    }
                    LocalDateTime resolvedTimestamp = this.clientTimestampPolicy.resolve(clientTimestamp, location,
                            operation);
                    ExecutionEntity finished = this.executionService.finishExecution(locked, resolvedTimestamp);
                    // Benachrichtigungen zu einem stark verspätet nachgemeldeten Ereignis werden
                    // unterdrueckt (Auftraggeber-Vorgabe, siehe ClientTimestampPolicy-Javadoc) -
                    // die ORIGINAL-Zeitstempel-Angabe entscheidet, nicht der ggf. per Drift-Toleranz
                    // ersetzte resolvedTimestamp. Issue #36: der Versand selbst läuft erst NACH dem
                    // Commit (AFTER_COMMIT-Event, siehe ExecutionNotificationListener), nicht mehr
                    // in dieser DB-Transaktion.
                    if (!this.clientTimestampPolicy.isNotificationSuppressed(clientTimestamp, location)) {
                        this.eventPublisher.publishEvent(
                                new ExecutionNotificationEvent(finished.getUser(), finished.getDevice(), aborted));
                    }
                    return toDto(finished);
                });
        return result.body();
    }

    /**
     * Setzt eine Ausführung zurück, ohne sie zu bezahlen - entspricht
     * {@code Execution#reset()}, aufgerufen vom Alt-Client, wenn das Einschalten der
     * Steckdose nach dem Anlegen der Ausführung fehlschlägt (siehe
     * {@link ExecutionService#resetExecution} Javadoc für die "finished=TRUE trotz reset()"-
     * Eigenheit, die hier bewusst 1:1 übernommen wird).
     */
    @PostMapping("/{id}/reset")
    public ExecutionDto reset(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        ExecutionEntity execution = this.scopeGuard.requireExecutionInScope(id, terminal);
        LocationEntity location = execution.getDevice().getLocation();

        IdempotentResult<ExecutionDto> result = this.idempotencyService.execute(idempotencyKey, location,
                "execution-reset", HttpStatus.OK.value(), ExecutionDto.class, () -> {
                    ExecutionEntity reset = this.executionService.resetExecution(execution);
                    return toDto(reset);
                });
        return result.body();
    }

    private ExecutionDto toDto(ExecutionEntity execution) {
        return ExecutionDto.of(execution, this.executionService.getPrice(execution));
    }
}
