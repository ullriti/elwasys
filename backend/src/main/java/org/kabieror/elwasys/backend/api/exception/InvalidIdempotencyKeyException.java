package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Der mitgeschickte {@code Idempotency-Key}-Header verletzt die Speichergrenze der Spalte
 * {@code terminal_idempotency_keys.idempotency_key} ({@code VARCHAR(64)}, siehe
 * {@code V4__create_terminal_idempotency_keys.sql}). Wird die Länge NICHT vorab geprüft,
 * scheitert erst {@code saveAndFlush} an der DB und die Operation (z.B. {@code finish}) kann
 * dauerhaft nicht persistiert werden (Issue #29). Diese Prüfung lehnt einen zu langen
 * Schlüssel früh mit 400 ab, BEVOR die fachliche Aktion läuft.
 */
public class InvalidIdempotencyKeyException extends ApiException {

    public InvalidIdempotencyKeyException(int maxLength) {
        super(HttpStatus.BAD_REQUEST, "invalid-idempotency-key", "Ungültiger Idempotency-Key",
                "Der Idempotency-Key darf höchstens " + maxLength + " Zeichen lang sein.");
    }
}
