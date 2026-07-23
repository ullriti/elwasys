package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.DashboardService.DeviceStatus;
import org.kabieror.elwasys.backend.service.DashboardService.LocationStatus;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link DashboardService} (Phase 3 AP3, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger der Datenbeschaffung aus {@code Portal/.../views/AdminDashboardView#loadData}
 * (Alt-Portal, Testfall P20: "Frei"/"Besetzt" wird direkt aus der laufenden Execution in der
 * DB abgeleitet, kein Client-Kontakt nötig).
 */
class DashboardServiceTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private DashboardService dashboardService;

    private UserEntity newUser() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        return this.userRepository.save(new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
    }

    private ProgramEntity newProgram(int maxDurationSeconds) {
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, maxDurationSeconds);
        program.setFlagfall(new BigDecimal("1.00"));
        program.setFreeDurationSeconds(0);
        return this.programRepository.save(program);
    }

    @Test
    void freeDeviceHasNoRunningExecutionAndNoRemainingTime() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));

        DeviceStatus status = this.dashboardService.getDeviceStatus(device);

        assertThat(status.isOccupied()).as("P20: freies Gerät").isFalse();
        assertThat(status.runningExecution()).isEmpty();
        assertThat(status.remainingTime()).isNull();
    }

    @Test
    void occupiedDeviceReportsTheRunningExecutionAndAPositiveRemainingTime() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
        ProgramEntity program = newProgram(3600);
        UserEntity user = newUser();

        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(device, program, user));

        DeviceStatus status = this.dashboardService.getDeviceStatus(device);

        assertThat(status.isOccupied()).as("P20: besetztes Gerät").isTrue();
        assertThat(status.runningExecution()).isPresent();
        assertThat(status.runningExecution().get().getId()).isEqualTo(execution.getId());
        assertThat(status.remainingTime()).isNotNull();
        assertThat(status.remainingTime().isPositive()).isTrue();
        assertThat(status.remainingTime()).isLessThanOrEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void anExpiredButNotYetFinishedExecutionIsNotReportedAsRunning() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
        ProgramEntity expiredProgram = newProgram(0);
        UserEntity user = newUser();
        this.executionService.startExecution(this.executionService.createExecution(device, expiredProgram, user));

        DeviceStatus status = this.dashboardService.getDeviceStatus(device);

        assertThat(status.isOccupied()).isFalse();
        assertThat(status.runningExecution()).isEmpty();
    }

    @Test
    void getLocationStatusesGroupsDevicesByTheirOwnLocationOnly() {
        LocationEntity locationA = this.locationRepository.save(new LocationEntity(Fixtures.unique("locA")));
        LocationEntity locationB = this.locationRepository.save(new LocationEntity(Fixtures.unique("locB")));
        DeviceEntity deviceA = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("devA"), 1, locationA));
        DeviceEntity deviceB = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("devB"), 1, locationB));

        List<LocationStatus> statuses = this.dashboardService.getLocationStatuses();

        LocationStatus statusA = statuses.stream().filter(s -> s.location().getId().equals(locationA.getId()))
                .findFirst().orElseThrow();
        LocationStatus statusB = statuses.stream().filter(s -> s.location().getId().equals(locationB.getId()))
                .findFirst().orElseThrow();

        assertThat(statusA.devices()).extracting(ds -> ds.device().getId()).containsExactly(deviceA.getId());
        assertThat(statusB.devices()).extracting(ds -> ds.device().getId()).containsExactly(deviceB.getId());
    }
    // Hinweis (Issue #30, Pre-Launch AP5): Die Geräte-Historie ist nicht mehr Teil von
    // DeviceStatus - das Dashboard-Grid lädt sie lazy seitenweise. Die Historie-Abfrage wird
    // daher in ExecutionServiceTest (getExecutions(device, pageable)/countExecutions) geprüft.
}
