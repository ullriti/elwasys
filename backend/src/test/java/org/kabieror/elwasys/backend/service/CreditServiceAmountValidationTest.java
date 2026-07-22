package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.NotEnoughCreditException;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Betragsvalidierung von {@link CreditService#inpayment}/{@link CreditService#payout}
 * (Issue #22 - AP3): ein Betrag {@code <= 0} muss abgelehnt werden und darf KEINEN
 * Buchungssatz erzeugen. Ohne diese Prüfung kehrte ein negativer Betrag die Buchung um
 * (eine "Einzahlung" von -50 umginge den Auszahlungs-Wächter, eine "Auszahlung" von -50 buchte
 * +50 mit widersprüchlichem Buchungstext) und ein Betrag 0 erzeugte einen leeren Buchungssatz -
 * in einem laut Datenmodell unveränderlichen Journal.
 */
class CreditServiceAmountValidationTest extends AbstractBackendIT {

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
    void negativeInpaymentIsRejectedAndBooksNothing() {
        UserEntity user = newUser();

        assertThatThrownBy(() -> this.creditService.inpayment(user, new BigDecimal("-50.00")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(this.creditService.getAccountingEntries(user)).isEmpty();
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("0.00");
    }

    @Test
    void zeroInpaymentIsRejectedAndBooksNothing() {
        UserEntity user = newUser();

        assertThatThrownBy(() -> this.creditService.inpayment(user, new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(this.creditService.getAccountingEntries(user)).isEmpty();
    }

    @Test
    void negativePayoutIsRejectedAndBooksNothing() {
        UserEntity user = newUser();
        this.creditService.inpayment(user, new BigDecimal("10.00"));

        assertThatThrownBy(() -> this.creditService.payout(user, new BigDecimal("-50.00")))
                .isInstanceOf(IllegalArgumentException.class);

        // Nur die eine gültige Einzahlung ist verbucht, der negative "Auszahlungs"-Versuch nicht.
        assertThat(this.creditService.getAccountingEntries(user)).hasSize(1);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("10.00");
    }

    @Test
    void zeroPayoutIsRejectedAndBooksNothing() {
        UserEntity user = newUser();
        this.creditService.inpayment(user, new BigDecimal("10.00"));

        assertThatThrownBy(() -> this.creditService.payout(user, new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(this.creditService.getAccountingEntries(user)).hasSize(1);
    }

    @Test
    void aRegularPositivePayoutStillWorksAndAnOverdraftIsStillRejected() {
        // Gegenprobe: der gültige Pfad bleibt unverändert; der Guthaben-Wächter greift weiter.
        UserEntity user = newUser();
        this.creditService.inpayment(user, new BigDecimal("10.00"));

        this.creditService.payout(user, new BigDecimal("4.00"));
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("6.00");

        assertThatExceptionOfType(NotEnoughCreditException.class)
                .isThrownBy(() -> this.creditService.payout(user, new BigDecimal("100.00")));
    }
}
