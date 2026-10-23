package org.kabieror.elwasys.backend.ui.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;

/** Summenzeile der Buchungsübersicht - reine Funktion, ohne Browser prüfbar. */
class AdminBookingsViewTest {

    private static CreditAccountingEntryEntity entry(String amount) {
        return new CreditAccountingEntryEntity(null, null, new BigDecimal(amount), LocalDateTime.of(2019, 1, 1, 0, 0),
                "x");
    }

    @Test
    void summaryCountsAndSumsCreditsAndDebits() {
        String text = AdminBookingsView.summaryText(List.of(entry("10.00"), entry("-4.00"), entry("5.00")));

        assertThat(text).startsWith("3 Buchungen").contains("15,00").contains("-4,00").contains("11,00");
    }

    @Test
    void summaryUsesSingularForOneBookingAndHandlesNone() {
        assertThat(AdminBookingsView.summaryText(List.of(entry("1.00")))).startsWith("1 Buchung ·");
        assertThat(AdminBookingsView.summaryText(List.of())).startsWith("0 Buchungen");
    }
}
