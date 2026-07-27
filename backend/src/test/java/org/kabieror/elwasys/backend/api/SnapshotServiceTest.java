package org.kabieror.elwasys.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.api.dto.SnapshotDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotProgramDto;
import org.kabieror.elwasys.backend.api.dto.SnapshotUserDto;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
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
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fachliche Zusammenstellung des Standort-Snapshots ({@link SnapshotService}, aus
 * {@code SnapshotController} herausgelöst - Issue #90). Ergänzt die HTTP-nahe Sicht aus
 * {@code SnapshotControllerTest} um die Fälle, die ohne Web-Kontext direkt am Dienst prüfbar
 * sind: Programm-Deduplizierung über mehrere Geräte und - sicherheitskritisch für die
 * Offline-Entscheidung am Terminal - die Gleichheit des Snapshot-Guthabens mit
 * {@link CreditService#getCredit} (der Snapshot nutzt seit Issue #90 die gebündelte
 * {@link CreditService#getCredits}-Variante; das Ergebnis muss identisch bleiben, inklusive
 * des Abzugs der Vor-Reservierung noch laufender Ausführungen).
 */
class SnapshotServiceTest extends AbstractBackendIT {

    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreditService creditService;
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private SnapshotService snapshotService;

    private LocationEntity newLocation() {
        return this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
    }

    private UserGroupEntity newAllowedGroup(LocationEntity location) {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);
        return group;
    }

    private ProgramEntity newFixedProgram(String flagfall) {
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal(flagfall));
        program.setFreeDurationSeconds(0);
        return this.programRepository.save(program);
    }

    private DeviceEntity newDeviceWithProgram(LocationEntity location, ProgramEntity program) {
        DeviceEntity device = new DeviceEntity(Fixtures.unique("dev"), 1, location);
        device.getPrograms().add(program);
        return this.deviceRepository.save(device);
    }

    @Test
    void programsAssignedToSeveralDevicesAppearExactlyOnce() {
        LocationEntity location = newLocation();
        ProgramEntity shared = newFixedProgram("3.00");
        newDeviceWithProgram(location, shared);
        newDeviceWithProgram(location, shared);

        SnapshotDto snapshot = this.snapshotService.buildSnapshot(location.getId());

        assertThat(snapshot.devices()).hasSize(2);
        assertThat(snapshot.programs()).extracting(SnapshotProgramDto::id).containsExactly(shared.getId());
    }

    @Test
    void userCreditMatchesTheSingleQueryVariantIncludingRunningExecutionReservation() {
        LocationEntity location = newLocation();
        UserGroupEntity group = newAllowedGroup(location);
        ProgramEntity program = newFixedProgram("2.50");
        DeviceEntity device = newDeviceWithProgram(location, program);

        // Nutzer mit Einzahlung UND laufender Ausführung: deren Maximalpreis ist vom Guthaben
        // vor-reserviert und muss auch im Snapshot abgezogen sein.
        UserEntity withReservation = this.userRepository.save(
                new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group));
        this.creditService.inpayment(withReservation, new BigDecimal("30.00"));
        this.executionService.startExecution(
                this.executionService.createExecution(device, program, withReservation));

        // Nutzer ganz ohne Buchung: 0,00 statt fehlendem Eintrag.
        UserEntity withoutBookings = this.userRepository.save(
                new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group));

        SnapshotDto snapshot = this.snapshotService.buildSnapshot(location.getId());

        assertThat(creditOf(snapshot, withReservation)).isEqualByComparingTo(
                this.creditService.getCredit(withReservation));
        assertThat(creditOf(snapshot, withReservation)).as("30,00 minus 2,50 Vor-Reservierung")
                .isEqualByComparingTo("27.50");
        assertThat(creditOf(snapshot, withoutBookings)).isEqualByComparingTo("0.00");
    }

    private BigDecimal creditOf(SnapshotDto snapshot, UserEntity user) {
        return snapshot.users().stream().filter(u -> u.id().equals(user.getId())).map(SnapshotUserDto::credit)
                .findFirst().orElseThrow();
    }
}
