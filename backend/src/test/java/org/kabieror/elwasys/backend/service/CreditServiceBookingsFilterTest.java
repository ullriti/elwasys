package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.BookingType;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.CreditAccountingEntryRepository;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link CreditService#findBookings} - die Buchungsübersicht des Portals
 * ({@code AdminBookingsView}). Die Tests grenzen sich über die Beschreibung (eindeutiger Präfix)
 * von Buchungen anderer Tests in derselben Datenbank ab und arbeiten mit festen Zeitstempeln
 * (kein Wanduhr-Bezug).
 */
class CreditServiceBookingsFilterTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ExecutionRepository executionRepository;
    @Autowired
    private CreditAccountingEntryRepository creditAccountingEntryRepository;
    @Autowired
    private CreditService creditService;

    private UserEntity newUser() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        return this.userRepository.save(new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
    }

    private ExecutionEntity newExecution(UserEntity user) {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
        ProgramEntity program = this.programRepository.save(
                new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 3600));
        return this.executionRepository.save(new ExecutionEntity(device, program, user));
    }

    private CreditAccountingEntryEntity book(UserEntity user, ExecutionEntity execution, String amount, String text,
            LocalDateTime date) {
        return this.creditAccountingEntryRepository.save(
                new CreditAccountingEntryEntity(user, execution, new BigDecimal(amount), date, text));
    }

    /** Alle Buchungen im festen Testzeitraum 2019 - jahresweit abgegrenzt von anderen Tests. */
    private List<Integer> ids(BookingType type, LocalDate from, LocalDate to, UserEntity user) {
        return this.creditService.findBookings(type, from, to).stream()
                .filter(e -> e.getUser().getId().equals(user.getId())).map(CreditAccountingEntryEntity::getId)
                .toList();
    }

    @Test
    void filtersByBookingType() {
        UserEntity user = newUser();
        CreditAccountingEntryEntity in = book(user, null, "10.00", "ein", LocalDateTime.of(2019, 1, 1, 10, 0));
        CreditAccountingEntryEntity out = book(user, null, "-4.00", "aus", LocalDateTime.of(2019, 1, 2, 10, 0));
        CreditAccountingEntryEntity run = book(user, newExecution(user), "-1.50", "lauf",
                LocalDateTime.of(2019, 1, 3, 10, 0));

        assertThat(ids(BookingType.INPAYMENT, null, null, user)).containsExactly(in.getId());
        assertThat(ids(BookingType.PAYOUT, null, null, user)).containsExactly(out.getId());
        assertThat(ids(BookingType.EXECUTION, null, null, user)).containsExactly(run.getId());
        assertThat(ids(null, null, null, user)).as("ohne Filter alle, neueste zuerst")
                .containsExactly(run.getId(), out.getId(), in.getId());
    }

    @Test
    void bookingTypeIsDerivedFromSignAndExecution() {
        UserEntity user = newUser();
        CreditAccountingEntryEntity in = book(user, null, "10.00", "ein", LocalDateTime.of(2019, 1, 1, 10, 0));
        CreditAccountingEntryEntity out = book(user, null, "-4.00", "aus", LocalDateTime.of(2019, 1, 2, 10, 0));
        CreditAccountingEntryEntity zero = book(user, null, "0.00", "null", LocalDateTime.of(2019, 1, 3, 10, 0));
        CreditAccountingEntryEntity run = book(user, newExecution(user), "-1.50", "lauf",
                LocalDateTime.of(2019, 1, 4, 10, 0));

        assertThat(BookingType.of(in)).isEqualTo(BookingType.INPAYMENT);
        assertThat(BookingType.of(out)).isEqualTo(BookingType.PAYOUT);
        assertThat(BookingType.of(run)).isEqualTo(BookingType.EXECUTION);
        assertThat(BookingType.of(zero)).isNull();
    }

    @Test
    void dateRangeIncludesBothBoundaryDaysCompletely() {
        UserEntity user = newUser();
        CreditAccountingEntryEntity before = book(user, null, "1.00", "vor", LocalDateTime.of(2019, 5, 9, 23, 59, 59));
        CreditAccountingEntryEntity firstDay = book(user, null, "1.00", "von", LocalDateTime.of(2019, 5, 10, 0, 0));
        CreditAccountingEntryEntity lastDay = book(user, null, "1.00", "bis", LocalDateTime.of(2019, 5, 12, 23, 59, 59));
        CreditAccountingEntryEntity after = book(user, null, "1.00", "nach", LocalDateTime.of(2019, 5, 13, 0, 0));

        LocalDate from = LocalDate.of(2019, 5, 10);
        LocalDate to = LocalDate.of(2019, 5, 12);
        assertThat(ids(null, from, to, user)).containsExactly(lastDay.getId(), firstDay.getId());
        assertThat(ids(null, from, null, user)).containsExactly(after.getId(), lastDay.getId(), firstDay.getId());
        assertThat(ids(null, null, to, user)).containsExactly(lastDay.getId(), firstDay.getId(), before.getId());
    }

    @Test
    void typeAndDateRangeCombine() {
        UserEntity user = newUser();
        book(user, null, "10.00", "alt", LocalDateTime.of(2019, 1, 1, 10, 0));
        CreditAccountingEntryEntity wanted = book(user, null, "10.00", "neu", LocalDateTime.of(2019, 6, 1, 10, 0));
        book(user, null, "-3.00", "falsche Art", LocalDateTime.of(2019, 6, 1, 11, 0));

        assertThat(ids(BookingType.INPAYMENT, LocalDate.of(2019, 6, 1), LocalDate.of(2019, 6, 1), user))
                .containsExactly(wanted.getId());
    }
}
