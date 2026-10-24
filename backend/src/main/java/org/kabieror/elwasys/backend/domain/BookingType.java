package org.kabieror.elwasys.backend.domain;

import java.math.BigDecimal;

/**
 * Fachliche Art einer Guthaben-Buchung ({@link CreditAccountingEntryEntity}). Die DB kennt
 * keine Spalte dafür; die Art ergibt sich - wie im Alt-Code ({@code User#inpayment},
 * {@code #payout}, {@code #payExecution}) - aus Vorzeichen und Ausführungsbezug: eine
 * Programmabrechnung hängt an einer Ausführung, Ein-/Auszahlungen nicht.
 */
public enum BookingType {

    /** Positive Buchung ohne Ausführung (Guthaben aufgeladen). */
    INPAYMENT("Einzahlungen"),
    /** Negative Buchung ohne Ausführung (Guthaben ausgezahlt). */
    PAYOUT("Auszahlungen"),
    /** Buchung zu einer Programmausführung (Abrechnung). */
    EXECUTION("Programmabrechnungen");

    private final String label;

    BookingType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return this.label;
    }

    /** Die Art einer vorhandenen Buchung; {@code null} für eine Buchung über 0,00 ohne Ausführung. */
    public static BookingType of(CreditAccountingEntryEntity entry) {
        if (entry.getExecutionId() != null) {
            return EXECUTION;
        }
        int sign = entry.getAmount().compareTo(BigDecimal.ZERO);
        return sign > 0 ? INPAYMENT : sign < 0 ? PAYOUT : null;
    }
}
