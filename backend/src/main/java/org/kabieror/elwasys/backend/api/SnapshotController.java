package org.kabieror.elwasys.backend.api;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.kabieror.elwasys.backend.api.dto.SnapshotDeviceDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotProgramDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotUserDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotUserGroupDto;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standort-Snapshot für die Offline-Buchungs-Vorbereitung (AP3, Phase 4, siehe
 * {@link SnapshotDto} Javadoc und docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen
 * am Terminal", Punkt 1 "Lokaler Daten-Snapshot"). Standort-Scope kommt implizit aus dem
 * Terminal-Token (kein Pfad-/Query-Parameter nötig, analog {@code LocationController#me}).
 */
@RestController
@RequestMapping("/api/v1/snapshot")
public class SnapshotController {

    private final LocationRepository locationRepository;

    private final DeviceRepository deviceRepository;

    private final UserRepository userRepository;

    private final CreditService creditService;

    public SnapshotController(LocationRepository locationRepository, DeviceRepository deviceRepository,
            UserRepository userRepository, CreditService creditService) {
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
    }

    @GetMapping
    public SnapshotDto snapshot(@AuthenticationPrincipal TerminalPrincipal terminal) {
        // Der Standort ist per Konstruktion vorhanden (er stammt aus dem authentifizierten
        // Terminal-Token, siehe TerminalTokenService#createToken), daher hier ohne weitere
        // Fehlerbehandlung nachgeladen - analog CardLoginController.
        LocationEntity location = this.locationRepository.findById(terminal.locationId()).orElseThrow();

        List<DeviceEntity> devices = this.deviceRepository.findByLocation_IdOrderByName(location.getId());

        // Programme: alle den Geräten dieses Standorts zugeordneten Programme, dedupliziert
        // (ein Programm kann mehreren Geräten desselben Standorts zugeordnet sein).
        Set<ProgramEntity> programSet = new LinkedHashSet<>();
        for (DeviceEntity device : devices) {
            programSet.addAll(device.getPrograms());
        }
        List<SnapshotProgramDto> programs = programSet.stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId())).map(SnapshotProgramDto::of).toList();

        // Benutzergruppen: exakt die am Standort zugelassenen (siehe SnapshotDto-Javadoc
        // "Scope-Entscheidung").
        Set<UserGroupEntity> validGroups = location.getValidUserGroups();
        List<SnapshotUserGroupDto> userGroups = validGroups.stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId())).map(SnapshotUserGroupDto::of).toList();

        // Benutzer: nur nicht geloeschte Benutzer, deren Gruppe am Standort zugelassen ist.
        List<SnapshotUserDto> users = this.userRepository.findByDeletedFalse().stream()
                .filter(u -> validGroups.contains(u.getGroup()))
                .map(u -> SnapshotUserDto.of(u, this.creditService.getCredit(u))).toList();

        List<SnapshotDeviceDto> deviceDtos = devices.stream().map(SnapshotDeviceDto::of).toList();

        int offlineMaxDurationMinutes = location.getOfflineMaxDurationMinutes() != null
                ? location.getOfflineMaxDurationMinutes()
                : LocationService.DEFAULT_OFFLINE_MAX_DURATION_MINUTES;
        return new SnapshotDto(location.getId(), location.getName(), LocalDateTime.now(), offlineMaxDurationMinutes,
                userGroups, users, deviceDtos, programs);
    }
}
