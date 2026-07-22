package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link ProgramService} (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/ProgramWindow} (Testfall P12) inkl. des
 * Lösch-Wächters aus {@code Portal/.../views/ProgramsView#deleteProgram}.
 */
class ProgramServiceTest extends AbstractBackendIT {

    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private ProgramService programService;

    @Test
    void createsAFixedProgram() {
        ProgramEntity created = this.programService.create(Fixtures.unique("Kurzwaschgang"), ProgramType.FIXED,
                new BigDecimal("2.50"), null, null, Duration.ofMinutes(30), Duration.ofMinutes(1), true,
                Duration.ofMinutes(5), true, Set.of());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getType()).isEqualTo(ProgramType.FIXED);
        assertThat(created.getFlagfall()).isEqualByComparingTo("2.50");
        assertThat(created.getMaxDurationSeconds()).isEqualTo((int) Duration.ofMinutes(30).getSeconds());
    }

    @Test
    void createsAndUpdatesADynamicProgram() {
        ProgramEntity created = this.programService.create(Fixtures.unique("Dynamikwaschgang"), ProgramType.DYNAMIC,
                new BigDecimal("0.50"), new BigDecimal("0.10"), TimeUnitType.MINUTES, Duration.ofMinutes(60),
                Duration.ofMinutes(2), true, Duration.ofMinutes(3), true, Set.of());

        assertThat(created.getTimeUnit()).isEqualTo(TimeUnitType.MINUTES);
        assertThat(created.getRate()).isEqualByComparingTo("0.10");

        ProgramEntity updated = this.programService.update(created, "Renamed", ProgramType.DYNAMIC,
                new BigDecimal("1.00"), new BigDecimal("0.20"), TimeUnitType.HOURS, Duration.ofMinutes(90),
                Duration.ofMinutes(5), false, Duration.ofMinutes(10), false, Set.of());

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getTimeUnit()).isEqualTo(TimeUnitType.HOURS);
        assertThat(updated.isAutoEnd()).isFalse();
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void deletingAProgramStillAssignedToADeviceFails() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        ProgramEntity program = this.programService.create(Fixtures.unique("prog"), ProgramType.FIXED,
                BigDecimal.ONE, null, null, Duration.ofMinutes(30), Duration.ZERO, true, Duration.ZERO, true,
                Set.of());
        DeviceEntity device = new DeviceEntity(Fixtures.unique("dev"), 1, location);
        device.getPrograms().add(program);
        this.deviceRepository.save(device);

        assertThatThrownBy(() -> this.programService.delete(program)).isInstanceOf(EntityInUseException.class)
                .hasMessageContaining("1 Gerät");

        assertThat(this.programRepository.findById(program.getId())).isPresent();
    }

    @Test
    void deletingAnUnassignedProgramSucceeds() {
        ProgramEntity program = this.programService.create(Fixtures.unique("prog"), ProgramType.FIXED,
                BigDecimal.ONE, null, null, Duration.ofMinutes(30), Duration.ZERO, true, Duration.ZERO, true,
                Set.of());

        this.programService.delete(program);

        assertThat(this.programRepository.findById(program.getId())).isEmpty();
    }
}
