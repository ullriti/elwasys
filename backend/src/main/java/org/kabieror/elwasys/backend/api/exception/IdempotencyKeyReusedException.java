package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Ein bereits verarbeiteter {@code Idempotency-Key} wird für einen ANDEREN Vorgang erneut
 * verwendet (Issue #41). Beim Replay wird ausschließlich per Schlüssel aufgelöst; ohne diese
 * Prüfung bekäme ein Terminal, das denselben Schlüssel versehentlich für zwei verschiedene
 * Ereignisse verwendet, die Fremd-Antwort zurück und die neue Aktion (z.B. {@code finish})
 * würde stillschweigend übersprungen. Statt dessen wird der Konflikt mit 409 sichtbar gemacht.
 */
public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException(String expectedOperation, String actualOperation) {
        super(HttpStatus.CONFLICT, "idempotency-key-reused", "Idempotency-Key bereits verwendet",
                "Der Idempotency-Key wurde bereits für den Vorgang '" + expectedOperation
                        + "' verwendet und kann nicht für '" + actualOperation + "' wiederverwendet werden.");
    }
}
