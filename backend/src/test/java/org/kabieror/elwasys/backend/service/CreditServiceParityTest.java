package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.NotEnoughCreditException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.kabieror.elwasys.backend.support.LegacyDataManagerFactory;
import org.kabieror.elwasys.backend.support.TestPostgres;
import org.kabieror.elwasys.common.DataManager;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Äquivalenznachweis für die Guthabenberechnung nach Buchungsfolgen (siehe
 * kb/05-migration-plan.md, AP2): dieselbe Datenbank wird nach jedem Schritt einmal über
 * {@code org.kabieror.elwasys.common.User#getCredit()} (Alt-Code, frischer
 * {@code DataManager} pro Prüfung, siehe {@link LegacyDataManagerFactory}) und einmal über
 * {@link CreditService#getCredit} gelesen - beide müssen exakt übereinstimmen, inklusive
 * der Zwischenzustände (Einzahlung, laufende unbezahlte Ausführung, abgeschlossene
 * Ausführung, Auszahlung).
 */
class CreditServiceParityTest extends AbstractBackendIT {

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

    private BigDecimal legacyCredit(Integer userId) throws Exception {
        DataManager legacy = LegacyDataManagerFactory.create(TestPostgres.jdbcUrl(), TestPostgres.username(),
                TestPostgres.password());
        return legacy.getUserById(userId).getCredit();
    }

    private void assertCreditParity(UserEntity user) throws Exception {
        BigDecimal newCredit = this.creditService.getCredit(user);
        BigDecimal legacyCredit = legacyCredit(user.getId());
        assertThat(newCredit).as("value").isEqualByComparingTo(legacyCredit);
        assertThat(newCredit.toPlainString()).as("exact textual representation").isEqualTo(
                legacyCredit.toPlainString());
    }

    @Test
    void creditEvolvesIdenticallyThroughInpaymentRunningAndFinishedExecutionAndPayout() throws Exception {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = this.userRepository.save(
                new UserEntity(Fixtures.unique("Credit User"), Fixtures.unique("credit-user"), group));

        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));

        // FIXED-Programm ohne Freiminuten: der Preis ist unabhängig von der tatsächlichen
        // (im Test winzigen) Laufzeit immer die Grundgebühr - macht den Test deterministisch.
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("5.00"));
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        // 0. Frisch angelegter Benutzer: Guthaben 0.00 auf beiden Seiten.
        assertCreditParity(user);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo(BigDecimal.ZERO);

        // 1. Einzahlung (User#inpayment).
        this.creditService.inpayment(user, new BigDecimal("100.00"), "Testeinzahlung");
        assertCreditParity(user);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("100.00");

        // 2. Eine neu angelegte, noch NICHT gestartete Ausführung mindert das Guthaben
        // bereits um ihren Maximalpreis (siehe ExecutionRepository/CreditService-Javadoc:
        // Beobachtung aus User#loadCredit, das gezielt KEINEN start-IS-NOT-NULL-Filter
        // verwendet).
        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        assertCreditParity(user);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("95.00");

        // 3. Starten ändert am Guthaben nichts (noch nicht bezahlt).
        execution = this.executionService.startExecution(execution);
        assertCreditParity(user);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("95.00");

        // 4. Beenden bucht den tatsächlichen Preis (hier: Grundgebühr, s.o.) ab - danach
        // zeigt sich derselbe Endstand wie vor dem Anlegen der Ausführung minus Preis.
        this.executionService.finishExecution(execution);
        assertCreditParity(user);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("95.00");

        // 5. Auszahlung.
        this.creditService.payout(user, new BigDecimal("20.00"), "Testauszahlung");
        assertCreditParity(user);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("75.00");
    }

    @Test
    void payoutBeyondCreditThrowsSameKindOfExceptionAsLegacyCode() throws Exception {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = this.userRepository.save(
                new UserEntity(Fixtures.unique("Poor User"), Fixtures.unique("poor-user"), group));

        assertThatThrownBy(() -> this.creditService.payout(user, new BigDecimal("1.00"))).isInstanceOf(
                NotEnoughCreditException.class);

        DataManager legacy = LegacyDataManagerFactory.create(TestPostgres.jdbcUrl(), TestPostgres.username(),
                TestPostgres.password());
        org.kabieror.elwasys.common.User legacyUser = legacy.getUserById(user.getId());
        assertThatThrownBy(() -> legacyUser.payout(new BigDecimal("1.00"))).isInstanceOf(
                org.kabieror.elwasys.common.NotEnoughCreditException.class);
    }
}
