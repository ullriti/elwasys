package org.kabieror.elwasys.backend.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.kabieror.elwasys.backend.api.dto.SnapshotDeviceDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotProgramDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotUserDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotUserGroupDto;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stellt den Standort-Snapshot zusammen, den {@link SnapshotController} ausliefert (Issue #90,
 * finale Review R3a): die Zusammenstellung ist reine Fachlogik (Programm-Deduplizierung,
 * Nutzerfilterung nach den am Standort zugelassenen Gruppen, Guthabenanreicherung) und gehört
 * damit nicht in den Controller - hier ist sie ohne Web-Kontext direkt testbar (siehe
 * {@code SnapshotServiceTest}), der Controller reicht nur noch die Standort-Id des
 * Terminal-Tokens durch.
 *
 * <p>Liegt bewusst im {@code api}-Paket (nicht in {@code service}): der Snapshot ist an die
 * {@link SnapshotDto}-Familie gebunden, und eine Abhängigkeit {@code service} → {@code api.dto}
 * würde die Schichtung umkehren. Vorbild ist
 * {@link org.kabieror.elwasys.backend.api.idempotency.IdempotencyService}, der aus demselben
 * Grund ebenfalls unterhalb von {@code api} liegt.
 */
@Service
public class SnapshotService {

    private final LocationRepository locationRepository;

    private final DeviceRepository deviceRepository;

    private final UserRepository userRepository;

    private final CreditService creditService;

    public SnapshotService(LocationRepository locationRepository, DeviceRepository deviceRepository,
            UserRepository userRepository, CreditService creditService) {
        this.locationRepository = locationRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
    }

    /**
     * Baut den vollständigen Snapshot eines Standorts.
     *
     * @param locationId der Standort, für den der Snapshot gilt - er stammt beim Aufruf über
     *                   die API aus dem authentifizierten Terminal-Token und ist daher per
     *                   Konstruktion vorhanden (siehe {@link SnapshotController}).
     */
    @Transactional(readOnly = true)
    public SnapshotDto buildSnapshot(Integer locationId) {
        LocationEntity location = this.locationRepository.findById(locationId).orElseThrow();

        List<DeviceEntity> devices = this.deviceRepository.findByLocation_IdOrderByName(location.getId());

        // Programme: alle den Geräten dieses Standorts zugeordneten Programme, dedupliziert
        // (ein Programm kann mehreren Geräten desselben Standorts zugeordnet sein).
        Set<ProgramEntity> programSet = new LinkedHashSet<>();
        for (DeviceEntity device : devices) {
            programSet.addAll(device.getPrograms());
        }
        List<SnapshotProgramDto> programs = programSet.stream().sorted(Comparator.comparing(ProgramEntity::getId))
                .map(SnapshotProgramDto::of).toList();

        // Benutzergruppen: exakt die am Standort zugelassenen (siehe SnapshotDto-Javadoc
        // "Scope-Entscheidung").
        Set<UserGroupEntity> validGroups = location.getValidUserGroups();
        List<SnapshotUserGroupDto> userGroups = validGroups.stream()
                .sorted(Comparator.comparing(UserGroupEntity::getId)).map(SnapshotUserGroupDto::of).toList();

        // Benutzer: nur nicht geloeschte Benutzer, deren Gruppe am Standort zugelassen ist.
        List<UserEntity> users = this.userRepository.findByDeletedFalse().stream()
                .filter(u -> validGroups.contains(u.getGroup())).toList();
        // Guthaben gebündelt in ZWEI Abfragen statt 2·N (Issue #90, finale Review R3a) - die
        // Batch-Variante ist fachlich identisch zu getCredit(u) je Benutzer (siehe
        // CreditService#getCredits, Issue #30).
        Map<Integer, BigDecimal> credits = this.creditService.getCredits(users);
        List<SnapshotUserDto> userDtos = users.stream()
                .map(u -> SnapshotUserDto.of(u, credits.get(u.getId()))).toList();

        List<SnapshotDeviceDto> deviceDtos = devices.stream().map(SnapshotDeviceDto::of).toList();

        int offlineMaxDurationMinutes = location.getOfflineMaxDurationMinutes() != null
                ? location.getOfflineMaxDurationMinutes()
                : LocationService.DEFAULT_OFFLINE_MAX_DURATION_MINUTES;
        return new SnapshotDto(location.getId(), location.getName(), LocalDateTime.now(), offlineMaxDurationMinutes,
                userGroups, userDtos, deviceDtos, programs);
    }
}
