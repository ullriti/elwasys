package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
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
 */
class CreditServiceAccountingHistoryTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreditService creditService;

    private UserEntity newUser() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        return this.userRepository.save(new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
    }

    @Test
    void getAccountingEntriesReturnsAllBookingsNewestFirstWithoutMutatingExistingRows() throws InterruptedException {
        UserEntity user = newUser();

        CreditAccountingEntryEntity first = this.creditService.inpayment(user, new BigDecimal("10.00"),
                "Erste Einzahlung");
        Thread.sleep(5);
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
    void getLastInpaymentIgnoresPayoutsAndReturnsTheMostRecentPositiveBooking() throws InterruptedException {
        UserEntity user = newUser();
        this.creditService.inpayment(user, new BigDecimal("5.00"), "alt");
        Thread.sleep(5);
        CreditAccountingEntryEntity latestInpayment = this.creditService.inpayment(user, new BigDecimal("7.00"),
                "neu");
        Thread.sleep(5);
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
}
