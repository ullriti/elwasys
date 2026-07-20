package org.kabieror.elwasys.backend.api.exception;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

/**
 * Entspricht der UI-Sperre in {@code ConfirmationViewController#isReady}/{@code selectProgram}
 * im Alt-Code ({@code !user.canAfford(maxPrice)}, Client-E2E-Fall C9 "zu wenig Guthaben"): das
 * Guthaben des Benutzers reicht nicht für den Maximalpreis des Programms. Die API prüft das
 * serverseitig vor dem Anlegen der Ausführung (siehe
 * {@link org.kabieror.elwasys.backend.service.CreditService#canAfford}), statt es nur wie im
 * Alt-Code als UI-Hinweis zu behandeln.
 *
 * <p>Bewusst {@code 402 Payment Required} statt {@code 409 Conflict}: der Grund für die
 * Ablehnung ist eindeutig "nicht genug Guthaben", ein selten, aber genau für diesen Zweck
 * vorgesehener HTTP-Status.
 */
public class InsufficientCreditException extends ApiException {

    public InsufficientCreditException(Integer userId, BigDecimal required, BigDecimal available) {
        super(HttpStatus.PAYMENT_REQUIRED, "insufficient-credit", "Guthaben nicht ausreichend",
                "Der Benutzer mit id=" + userId + " hat nicht genug Guthaben (verfügbar=" + available.toPlainString()
                        + ", benötigt bis zu " + required.toPlainString() + ").");
    }
}
