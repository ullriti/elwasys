package org.kabieror.elwasys.backend.exception;

/**
 * Entspricht dem {@code OldPasswordValidator} aus {@code Portal/.../components/ChangePasswordWindow}
 * (Alt-Portal, Testfall P16): wird geworfen, wenn beim Ändern des eigenen Passworts das
 * angegebene aktuelle Passwort nicht mit dem gespeicherten Hash übereinstimmt.
 */
public class InvalidCurrentPasswordException extends RuntimeException {

    public InvalidCurrentPasswordException() {
        super("Das aktuelle Passwort ist nicht korrekt.");
    }
}
