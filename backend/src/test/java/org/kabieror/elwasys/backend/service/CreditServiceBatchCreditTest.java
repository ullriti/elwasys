package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Issue #30 (Pre-Launch AP5): Die Guthaben-Spalte der Benutzerliste lädt die Guthaben aller
 * Benutzer gebündelt über {@link CreditService#getCredits(List)}. Dieser Test sichert ab, dass
 * das Bündel-Ergebnis fachlich EXAKT dem der Einzelabfrage {@link CreditService#getCredit}
 * entspricht - inklusive des Abzugs der Vor-Reservierung (Maximalpreis) noch nicht
 * abgeschlossener Ausführungen.
 */
class CreditServiceBatchCreditTest extends AbstractBackendIT {

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
    private CreditService creditService;
    @Autowired
    private ExecutionService executionService;

    private UserEntity newUser() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        return this.userRepository.save(new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
    }

    @Test
    void getCreditsMatchesGetCreditForEveryUserIncludingReservations() {
        // Nutzer A: nur Einzahlungen.
        UserEntity userA = newUser();
        this.creditService.inpayment(userA, new BigDecimal("20.00"));

        // Nutzer B: Einzahlung UND eine laufende (noch nicht abgeschlossene) Ausführung, deren
        // Maximalpreis vom Guthaben vor-reserviert wird.
        UserEntity userB = newUser();
        this.creditService.inpayment(userB, new BigDecimal("30.00"));
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("2.50"));
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);
        this.executionService.startExecution(this.executionService.createExecution(device, program, userB));

        // Nutzer C: gar keine Buchung -> 0,00.
        UserEntity userC = newUser();

        Map<Integer, BigDecimal> credits = this.creditService.getCredits(List.of(userA, userB, userC));

        assertThat(credits.get(userA.getId())).isEqualByComparingTo(this.creditService.getCredit(userA));
        assertThat(credits.get(userB.getId())).isEqualByComparingTo(this.creditService.getCredit(userB));
        assertThat(credits.get(userC.getId())).isEqualByComparingTo(this.creditService.getCredit(userC));

        // Konkrete Erwartungswerte zur Absicherung der Semantik.
        assertThat(credits.get(userA.getId())).isEqualByComparingTo("20.00");
        assertThat(credits.get(userB.getId())).as("30,00 minus 2,50 Vor-Reservierung").isEqualByComparingTo("27.50");
        assertThat(credits.get(userC.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    void getCreditsReturnsEmptyMapForEmptyInput() {
        assertThat(this.creditService.getCredits(List.of())).isEmpty();
    }
}
