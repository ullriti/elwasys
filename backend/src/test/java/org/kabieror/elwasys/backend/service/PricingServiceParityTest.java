package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.kabieror.elwasys.backend.support.TestPostgres;
import org.kabieror.elwasys.common.DataManager;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Stärkster Äquivalenz-Nachweis für die Preisberechnung (siehe kb/05-migration-plan.md,
 * AP2 "Entscheidungen"): dieselbe Datenbankzeile wird einmal über den Alt-Code
 * ({@code org.kabieror.elwasys.common.Program#getPrice}, per Common als test-scope
 * Dependency) und einmal über den neuen {@link PricingService} berechnet - die Ergebnisse
 * müssen bitgenau (inkl. {@link BigDecimal}-Skala) übereinstimmen.
 *
 * <p>Szenarien decken alle Programmtypen (FIXED/DYNAMIC), alle Zeiteinheiten
 * (SECONDS/MINUTES/HOURS inkl. der Ganzzahl-Abschneide-Eigenheit bei MINUTES/HOURS), die
 * Freiminuten-Grenze und alle Rabatttypen (NONE/FIX/FACTOR, letzterer bewusst mit einem
 * Wert, der die {@code new BigDecimal(double)}-Fließkomma-Eigenheit des Alt-Codes
 * sichtbar macht) ab.
 */
class PricingServiceParityTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private PricingService pricingService;

    private UserGroupEntity newGroup(DiscountType type, double value) {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique("group"), type, value));
    }

    private UserEntity newUser(UserGroupEntity group) {
        UserEntity u = new UserEntity(Fixtures.unique("Parity User"), Fixtures.unique("parity-user"), group);
        u.setCardIdsRaw("");
        return this.userRepository.save(u);
    }

    private DataManager legacyDataManager() throws Exception {
        return org.kabieror.elwasys.backend.support.LegacyDataManagerFactory.create(TestPostgres.jdbcUrl(),
                TestPostgres.username(), TestPostgres.password());
    }

    private void assertParity(ProgramEntity program, Duration duration, UserEntity user) throws Exception {
        BigDecimal newPrice = this.pricingService.getPrice(program, duration, user);

        DataManager legacy = legacyDataManager();
        org.kabieror.elwasys.common.Program legacyProgram = legacy.getProgramById(program.getId());
        org.kabieror.elwasys.common.User legacyUser = legacy.getUserById(user.getId());

        BigDecimal legacyPrice = legacyProgram.getPrice(duration, legacyUser);

        assertThat(newPrice).as("value").isEqualByComparingTo(legacyPrice);
        assertThat(newPrice.scale()).as("scale (BigDecimal precision must match exactly)").isEqualTo(
                legacyPrice.scale());
        assertThat(newPrice.toPlainString()).as("exact textual representation").isEqualTo(
                legacyPrice.toPlainString());
    }

    @Test
    void fixedProgramNoDiscount() throws Exception {
        UserGroupEntity group = newGroup(DiscountType.NONE, 0);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("Fixed"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("10.00"));
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        assertParity(program, Duration.ofMinutes(30), user);
    }

    @Test
    void fixedProgramZeroFlagfallStillHasScaleTwoNotBigDecimalZero() throws Exception {
        // Beobachtung (siehe kb/05-migration-plan.md): ein FIXED-Programm mit einer als
        // "0.00" (Skala 2) gepflegten Grundgebühr liefert NICHT dasselbe BigDecimal wie
        // BigDecimal.ZERO (Skala 0) - Program#getPrice liefert hier price=flagfall direkt
        // durch, ohne die Freiminuten-Sonderbehandlung. Dieser Test beweist, dass Alt und
        // Neu hier exakt dasselbe (überraschende) Objekt liefern.
        UserGroupEntity group = newGroup(DiscountType.NONE, 0);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("FixedZero"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("0.00"));
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        BigDecimal price = this.pricingService.getPrice(program, Duration.ofMinutes(30), user);
        assertThat(price.scale()).isEqualTo(2);
        assertThat(price.equals(BigDecimal.ZERO)).as("0.00 (scale 2) must NOT .equals() BigDecimal.ZERO (scale 0)")
                .isFalse();

        assertParity(program, Duration.ofMinutes(30), user);
    }

    @Test
    void dynamicProgramSeconds() throws Exception {
        UserGroupEntity group = newGroup(DiscountType.NONE, 0);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("DynSec"), ProgramType.DYNAMIC, 3600);
        program.setFlagfall(new BigDecimal("1.00"));
        program.setRate(new BigDecimal("0.50"));
        program.setTimeUnit(TimeUnitType.SECONDS);
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        assertParity(program, Duration.ofSeconds(125), user);
    }

    @Test
    void dynamicProgramMinutesTruncatesLikeIntegerDivision() throws Exception {
        // 125s = 2min5s -> Ganzzahl-Division 125/60 = 2 (nicht 2.08333...) - Alt-Code-Detail,
        // siehe PricingService#getDynamicPrice.
        UserGroupEntity group = newGroup(DiscountType.NONE, 0);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("DynMin"), ProgramType.DYNAMIC, 3600);
        program.setFlagfall(new BigDecimal("1.00"));
        program.setRate(new BigDecimal("0.50"));
        program.setTimeUnit(TimeUnitType.MINUTES);
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        BigDecimal price = this.pricingService.getPrice(program, Duration.ofSeconds(125), user);
        assertThat(price).isEqualByComparingTo("2.00");
        assertParity(program, Duration.ofSeconds(125), user);
    }

    @Test
    void dynamicProgramHoursTruncatesLikeIntegerDivision() throws Exception {
        UserGroupEntity group = newGroup(DiscountType.NONE, 0);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("DynHour"), ProgramType.DYNAMIC, 30000);
        program.setFlagfall(new BigDecimal("1.00"));
        program.setRate(new BigDecimal("0.50"));
        program.setTimeUnit(TimeUnitType.HOURS);
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        // 7300s = 2h1min40s -> 7300/3600 = 2 (Ganzzahldivision)
        BigDecimal price = this.pricingService.getPrice(program, Duration.ofSeconds(7300), user);
        assertThat(price).isEqualByComparingTo("2.00");
        assertParity(program, Duration.ofSeconds(7300), user);
    }

    @Test
    void freeDurationBoundaryIsInclusiveZero() throws Exception {
        UserGroupEntity group = newGroup(DiscountType.NONE, 0);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("FreeDur"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("10.00"));
        program.setFreeDurationSeconds(60);
        program = this.programRepository.save(program);

        // Exakt an der Grenze (<=) ist noch kostenlos.
        assertThat(this.pricingService.getPrice(program, Duration.ofSeconds(60), user)).isEqualByComparingTo(
                BigDecimal.ZERO);
        assertParity(program, Duration.ofSeconds(60), user);
        // Eine Sekunde darüber kostet bereits den vollen Preis.
        assertParity(program, Duration.ofSeconds(61), user);
    }

    @Test
    void discountTypeFactorReproducesBinaryFloatingPointArtifact() throws Exception {
        // new BigDecimal(double) statt BigDecimal.valueOf(double): 0.1 als double ist
        // binär nicht exakt darstellbar. Der Alt-Code übernimmt genau diesen
        // ungenauen Wert - dieser Test beweist, dass der neue Service exakt dasselbe tut
        // (nicht die "sauberere" valueOf-Variante).
        UserGroupEntity group = newGroup(DiscountType.FACTOR, 0.1);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("Factor"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("10.00"));
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        BigDecimal price = this.pricingService.getPrice(program, Duration.ofMinutes(30), user);
        // 10.00 - 10.00 * 0.1(binär ungenau) -> viele Nachkommastellen, NICHT exakt 9.00 -
        // weder im Text noch im Wert (0.1 ist als double nicht exakt darstellbar, der
        // Fehler pflanzt sich in die Multiplikation fort).
        assertThat(price.toPlainString()).isNotEqualTo("9.00");
        assertThat(price).isNotEqualByComparingTo("9.00");
        assertThat(price).isCloseTo(new BigDecimal("9.00"), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));

        assertParity(program, Duration.ofMinutes(30), user);
    }

    @Test
    void discountTypeFixSubtractsFlatAmount() throws Exception {
        UserGroupEntity group = newGroup(DiscountType.FIX, 2.5);
        UserEntity user = newUser(group);

        ProgramEntity program = new ProgramEntity(Fixtures.unique("Fix"), ProgramType.FIXED, 3600);
        program.setFlagfall(new BigDecimal("10.00"));
        program.setFreeDurationSeconds(0);
        program = this.programRepository.save(program);

        assertParity(program, Duration.ofMinutes(30), user);
    }
}
