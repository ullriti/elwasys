package org.kabieror.elwasys.backend.exception;

/**
 * Entspricht {@code common.User#passwordResetKeyIsValid()} (Alt-Portal, dort in
 * {@code ResetPasswordWindow} implizit über den Aufrufer geprüft): wird geworfen, wenn ein
 * über die öffentliche Passwort-Reset-Ansicht ({@code ResetPasswordView}) eingereichter
 * Schlüssel unbekannt, bereits verbraucht oder abgelaufen ist. Ein Reset-Schlüssel ist
 * Einmalgebrauch - siehe {@code PasswordResetService#resetPassword}.
 */
public class InvalidOrExpiredResetTokenException extends RuntimeException {

    public InvalidOrExpiredResetTokenException() {
        super("Dieser Link zum Zurücksetzen des Passworts ist ungültig oder abgelaufen.");
    }
}
