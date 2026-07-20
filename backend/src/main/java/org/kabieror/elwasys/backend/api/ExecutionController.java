package org.kabieror.elwasys.backend.api;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Duration;
import org.kabieror.elwasys.backend.api.dto.ExecutionDto;
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
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Execution-Lebenszyklus über die Terminal-API (AP4, siehe kb/05-migration-plan.md):
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
 */
@RestController
@RequestMapping("/api/v1/executions")
public class ExecutionController {

    private final ProgramRepository programRepository;

    private final UserRepository userRepository;

    private final PermissionService permissionService;

    private final PricingService pricingService;

    private final CreditService creditService;

    private final ExecutionService executionService;

    private final TerminalScopeGuard scopeGuard;

    public ExecutionController(ProgramRepository programRepository, UserRepository userRepository,
            PermissionService permissionService, PricingService pricingService, CreditService creditService,
            ExecutionService executionService, TerminalScopeGuard scopeGuard) {
        this.programRepository = programRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.pricingService = pricingService;
        this.creditService = creditService;
        this.executionService = executionService;
        this.scopeGuard = scopeGuard;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionDto start(@AuthenticationPrincipal TerminalPrincipal terminal,
            @Valid @RequestBody ExecutionStartRequest request) {
        DeviceEntity device = this.scopeGuard.requireDeviceInScope(request.deviceId(), terminal);
        ProgramEntity program = this.programRepository.findById(request.programId()).orElseThrow(
                () -> new ProgramNotFoundException(request.programId()));
        UserEntity user = this.userRepository.findById(request.userId()).orElseThrow(
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

        BigDecimal maxPrice = this.pricingService.getPrice(program, Duration.ofSeconds(program.getMaxDurationSeconds()),
                user);
        if (!this.creditService.canAfford(user, maxPrice)) {
            throw new InsufficientCreditException(user.getId(), maxPrice, this.creditService.getCredit(user));
        }

        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        execution = this.executionService.startExecution(execution);
        return toDto(execution);
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
    public ExecutionDto finish(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id) {
        return finishOrAbort(terminal, id);
    }

    /**
     * Vorzeitiger Abbruch durch den Benutzer. Persistenzseitig identisch zu {@link #finish}
     * (siehe {@link ExecutionService#finishExecution} Javadoc) - eigener Endpunkt für eine
     * klare API-Semantik und künftige Erweiterbarkeit (z.B. abweichende
     * Benachrichtigungstexte, die weiterhin im Terminal entstehen).
     */
    @PostMapping("/{id}/abort")
    public ExecutionDto abort(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id) {
        return finishOrAbort(terminal, id);
    }

    private ExecutionDto finishOrAbort(TerminalPrincipal terminal, Integer id) {
        ExecutionEntity execution = this.scopeGuard.requireExecutionInScope(id, terminal);
        if (execution.isFinished()) {
            throw new ExecutionAlreadyFinishedException(id);
        }
        execution = this.executionService.finishExecution(execution);
        return toDto(execution);
    }

    /**
     * Setzt eine Ausführung zurück, ohne sie zu bezahlen - entspricht
     * {@code Execution#reset()}, aufgerufen vom Alt-Client, wenn das Einschalten der
     * Steckdose nach dem Anlegen der Ausführung fehlschlägt (siehe
     * {@link ExecutionService#resetExecution} Javadoc für die "finished=TRUE trotz reset()"-
     * Eigenheit, die hier bewusst 1:1 übernommen wird).
     */
    @PostMapping("/{id}/reset")
    public ExecutionDto reset(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id) {
        ExecutionEntity execution = this.scopeGuard.requireExecutionInScope(id, terminal);
        execution = this.executionService.resetExecution(execution);
        return toDto(execution);
    }

    private ExecutionDto toDto(ExecutionEntity execution) {
        return ExecutionDto.of(execution, this.executionService.getPrice(execution));
    }
}
