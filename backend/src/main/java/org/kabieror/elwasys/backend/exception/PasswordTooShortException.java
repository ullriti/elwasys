package org.kabieror.elwasys.backend.exception;

/**
 * Wird geworfen, wenn ein neu zu setzendes Passwort die serverseitige Mindestlänge
 * unterschreitet (Issue #44, Pre-Launch AP4, ADR 0018).
 *
 * <p><b>Warum zentral:</b> die Regel wird an genau EINER Stelle
 * ({@code PasswordService#setNewPassword}) erzwungen, damit ALLE Passwort-Pfade – Ändern-
 * Dialog, öffentlicher Reset-Link und Admin-Reset – gleichermaßen geschützt sind. Bewusste
 * Verschärfung gegenüber dem Alt-Portal (das jedes nicht-leere Passwort akzeptierte), in
 * ADR 0018 freigegeben.
 */
public class PasswordTooShortException extends RuntimeException {

    public PasswordTooShortException(int minLength) {
        super("Das Passwort muss mindestens " + minLength + " Zeichen lang sein.");
    }
}
