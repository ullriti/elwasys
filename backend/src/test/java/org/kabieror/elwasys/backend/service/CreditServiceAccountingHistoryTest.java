package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.CreditAccountingEntryRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link CreditService#getAccountingEntries} und {@link CreditService#getLastInpayment}
 * (Phase 3 AP3, siehe docs/kb/05-migration-plan.md) - fachliche Nachfolger von
 * {@code DataManager#getAccountingEntries}/{@code #getLastInpayment}, verwendet vom
 * "Umsätze ansehen"-Dialog ({@code CreditHistoryDialog}, Alt-Vorbild
 * {@code CreditAccountingWindow}) und vom Benutzer-Dashboard ({@code UserDashboardView},
 * Alt-Vorbild {@code UsersDashboardView}, Testfall P15).
 *
 * <p>Sichert zusätzlich indirekt die <b>Unveränderlichkeit der Buchungen</b> ab: jeder Aufruf
 * von {@link CreditService#inpayment}/{@link CreditService#payout} erzeugt einen neuen, nie
 * mehr angefassten Datensatz - ein wiederholter Abruf über {@code getAccountingEntries}
 * liefert daher exakt denselben Betrag/Text wie beim Anlegen, unabhängig davon, wie viele
 * weitere Buchungen seither hinzugekommen sind.
 *
 * <p><b>Determinismus (Issue #88):</b> die Reihenfolge-Fälle kamen früher über
 * {@code Thread.sleep(5)} zwischen den Buchungen zustande - auf einer langsamen/überlasteten
 * CI-Maschine ein zu knapper Abstand und laut AGENTS.md unzulässig. Stattdessen greift jetzt
 * das zweite Sortierkriterium {@code id DESC} (siehe
 * {@link CreditAccountingEntryRepository#findByUser_IdOrderByDateDescIdDesc}), das die
 * Reihenfolge unabhängig von der Zeitauflösung festlegt; dass PRIMÄR weiterhin der Zeitstempel
 * entscheidet, prüfen die beiden {@code ...OrdersByDate...}-Fälle mit bewusst weit
 * auseinanderliegenden, festen Zeitstempeln (Muster aus Issue #40, vgl.
 * {@code ExecutionServiceTest#startExecutionSetsStartTimeOnlyOnce}).
 */
class CreditServiceAccountingHistoryTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreditAccountingEntryRepository creditAccountingEntryRepository;
    @Autowired
    private CreditService creditService;

    private UserEntity newUser() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        return this.userRepository.save(new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
    }

    /**
     * Legt eine Buchung mit einem FESTEN Zeitstempel direkt über das Repository an (nicht über
     * {@link CreditService}, dessen fachliche API bewusst keinen von außen vorgegebenen
     * Buchungszeitpunkt zulässt) - nur so lässt sich die Datums-Sortierung unabhängig von der
     * Anlegereihenfolge/Wanduhr beweisen.
     */
    private CreditAccountingEntryEntity bookedAt(UserEntity user, String amount, String description,
            LocalDateTime date) {
        return this.creditAccountingEntryRepository.save(
                new CreditAccountingEntryEntity(user, null, new BigDecimal(amount), date, description));
    }

    @Test
    void getAccountingEntriesReturnsAllBookingsNewestFirstWithoutMutatingExistingRows() {
        UserEntity user = newUser();

        CreditAccountingEntryEntity first = this.creditService.inpayment(user, new BigDecimal("10.00"),
                "Erste Einzahlung");
        CreditAccountingEntryEntity second = this.creditService.payout(user, new BigDecimal("4.00"),
                "Erste Auszahlung");

        List<CreditAccountingEntryEntity> entries = this.creditService.getAccountingEntries(user);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getId()).as("neueste Buchung zuerst").isEqualTo(second.getId());
        assertThat(entries.get(1).getId()).isEqualTo(first.getId());

        // Die ursprünglich gebuchten Werte sind unverändert abrufbar - kein Update hat
        // stattgefunden.
        assertThat(entries.get(1).getAmount()).isEqualByComparingTo("10.00");
        assertThat(entries.get(1).getDescription()).isEqualTo("Erste Einzahlung");
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("-4.00");
        assertThat(entries.get(0).getDescription()).isEqualTo("Erste Auszahlung");
    }

    @Test
    void getLastInpaymentIgnoresPayoutsAndReturnsTheMostRecentPositiveBooking() {
        UserEntity user = newUser();
        this.creditService.inpayment(user, new BigDecimal("5.00"), "alt");
        CreditAccountingEntryEntity latestInpayment = this.creditService.inpayment(user, new BigDecimal("7.00"),
                "neu");
        this.creditService.payout(user, new BigDecimal("3.00"), "Auszahlung danach");

        Optional<CreditAccountingEntryEntity> lastInpayment = this.creditService.getLastInpayment(user);

        assertThat(lastInpayment).isPresent();
        assertThat(lastInpayment.get().getId()).isEqualTo(latestInpayment.getId());
    }

    @Test
    void getLastInpaymentIsEmptyWithoutAnyPositiveBooking() {
        UserEntity user = newUser();

        assertThat(this.creditService.getLastInpayment(user)).isEmpty();
    }

    @Test
    void getAccountingEntriesOrdersByBookingDateNotByInsertionOrder() {
        UserEntity user = newUser();
        // Bewusst "verkehrt herum" angelegt: die ZUERST gespeicherte Buchung trägt den
        // JÜNGEREN Zeitstempel. Nur wenn nach date sortiert wird (und nicht nach Id/Anlage-
        // reihenfolge), steht sie am Ende der Liste vorn.
        CreditAccountingEntryEntity younger = bookedAt(user, "10.00", "spätere Einzahlung",
                LocalDateTime.of(2021, 6, 6, 15, 30, 0));
        CreditAccountingEntryEntity older = bookedAt(user, "5.00", "frühere Einzahlung",
                LocalDateTime.of(2020, 1, 1, 10, 0, 0));

        List<CreditAccountingEntryEntity> entries = this.creditService.getAccountingEntries(user);

        assertThat(entries).extracting(CreditAccountingEntryEntity::getId)
                .as("neuester Buchungszeitpunkt zuerst, unabhängig von der Anlegereihenfolge")
                .containsExactly(younger.getId(), older.getId());
    }

    @Test
    void bookingsWithTheSameTimestampKeepTheOrderOfTheirIds() {
        // Regressionstest zum eigentlichen Grund für "ORDER BY date DESC, id DESC" (#88): der
        // Buchungszeitpunkt kommt aus LocalDateTime.now() und kann für zwei Buchungen identisch
        // sein (gleiche Zeitauflösung). Ohne den id-Tiebreak ist die Reihenfolge dann undefiniert
        // und die Historie kann zwischen zwei Abrufen springen. Die Tests mit weit
        // auseinanderliegenden Zeitstempeln fangen genau das NICHT - sie wären auch ohne den
        // Tiebreak grün.
        UserEntity user = newUser();
        LocalDateTime sameInstant = LocalDateTime.of(2022, 3, 3, 12, 0, 0);
        CreditAccountingEntryEntity first = bookedAt(user, "5.00", "erste Buchung", sameInstant);
        CreditAccountingEntryEntity second = bookedAt(user, "6.00", "zweite Buchung", sameInstant);

        assertThat(this.creditService.getAccountingEntries(user))
                .extracting(CreditAccountingEntryEntity::getId)
                .as("bei identischem Zeitpunkt entscheidet die Id: zuletzt gebucht zuerst")
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void lastInpaymentWithTheSameTimestampIsTheOneBookedLast() {
        UserEntity user = newUser();
        LocalDateTime sameInstant = LocalDateTime.of(2022, 3, 3, 12, 0, 0);
        bookedAt(user, "5.00", "erste Einzahlung", sameInstant);
        CreditAccountingEntryEntity second = bookedAt(user, "6.00", "zweite Einzahlung", sameInstant);

        assertThat(this.creditService.getLastInpayment(user)).get()
                .extracting(CreditAccountingEntryEntity::getId).isEqualTo(second.getId());
    }

    @Test
    void getLastInpaymentOrdersByBookingDateNotByInsertionOrder() {
        UserEntity user = newUser();
        CreditAccountingEntryEntity younger = bookedAt(user, "10.00", "spätere Einzahlung",
                LocalDateTime.of(2021, 6, 6, 15, 30, 0));
        bookedAt(user, "5.00", "frühere Einzahlung", LocalDateTime.of(2020, 1, 1, 10, 0, 0));

        assertThat(this.creditService.getLastInpayment(user)).get()
                .extracting(CreditAccountingEntryEntity::getId).isEqualTo(younger.getId());
    }
}
