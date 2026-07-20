package org.kabieror.elwasys.backend.exception;

/**
 * Entspricht {@code org.kabieror.elwasys.common.NotEnoughCreditException}: wird geworfen,
 * wenn eine Auszahlung/Belastung das verfügbare Guthaben eines Benutzers übersteigen würde.
 */
public class NotEnoughCreditException extends RuntimeException {

    public NotEnoughCreditException() {
        super("Not enough credit available.");
    }
}
