package org.kabieror.elwasys.backend.api;

import java.time.Duration;
import java.util.List;
import org.kabieror.elwasys.backend.api.dto.DeviceDto;
import org.kabieror.elwasys.backend.api.dto.ProgramDto;
import org.kabieror.elwasys.backend.api.exception.UserNotFoundException;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Geräte-/Programmliste für den Standort des Terminal-Tokens (AP4, siehe
 * kb/05-migration-plan.md). Entspricht dem Ladepfad {@code DataManager#getDevicesToDisplay}
 * (Geräteliste) + {@code Device#getPrograms(User)} (Programmliste je Gerät) im Alt-Code,
 * berechtigungsgeprüft über {@link PermissionService} (1:1-Portierung aus AP2).
 *
 * <p>Standort-Scope strikt durchgesetzt: nur Geräte am Standort des Tokens werden
 * zurückgegeben bzw. sind per Id erreichbar (siehe {@link TerminalScopeGuard}, vgl.
 * Client-E2E-Fall C16 "standortfremdes Gerät").
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;

    private final UserRepository userRepository;

    private final PermissionService permissionService;

    private final PricingService pricingService;

    private final ExecutionService executionService;

    private final TerminalScopeGuard scopeGuard;

    public DeviceController(DeviceRepository deviceRepository, UserRepository userRepository,
            PermissionService permissionService, PricingService pricingService, ExecutionService executionService,
            TerminalScopeGuard scopeGuard) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.pricingService = pricingService;
        this.executionService = executionService;
        this.scopeGuard = scopeGuard;
    }

    @GetMapping
    public List<DeviceDto> list(@AuthenticationPrincipal TerminalPrincipal terminal, @RequestParam Integer userId) {
        UserEntity user = requireUser(userId);
        return this.deviceRepository.findByLocation_IdOrderByName(terminal.locationId()).stream()
                .map(device -> toDto(device, user)).toList();
    }

    @GetMapping("/{id}")
    public DeviceDto get(@AuthenticationPrincipal TerminalPrincipal terminal, @PathVariable Integer id,
            @RequestParam Integer userId) {
        DeviceEntity device = this.scopeGuard.requireDeviceInScope(id, terminal);
        UserEntity user = requireUser(userId);
        return toDto(device, user);
    }

    private UserEntity requireUser(Integer userId) {
        return this.userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private DeviceDto toDto(DeviceEntity device, UserEntity user) {
        boolean usable = this.permissionService.isDeviceUsableByUser(device, user);
        boolean occupied = this.executionService.getRunningExecution(device).isPresent();
        List<ProgramDto> programs = this.permissionService.getAvailablePrograms(device, user).stream()
                .map(program -> toProgramDto(program, user)).toList();
        return DeviceDto.of(device, usable, occupied, programs);
    }

    private ProgramDto toProgramDto(ProgramEntity program, UserEntity user) {
        Duration maxDuration = Duration.ofSeconds(program.getMaxDurationSeconds());
        return ProgramDto.of(program, this.pricingService.getPrice(program, maxDuration, user));
    }
}
