package org.kabieror.elwasys.backend.exception;

/**
 * Wird beim Anlegen/Bearbeiten eines Benutzers geworfen, wenn der gewünschte Benutzername –
 * case-insensitiv verglichen – bereits einem anderen, nicht gelöschten Benutzer gehört
 * (Issue #23, Pre-Launch AP4).
 *
 * <p><b>Warum:</b> die DB-{@code UNIQUE}-Constraint auf {@code users.username} ist
 * case-sensitiv, der Portal-Login vergleicht aber case-insensitiv
 * ({@code findByUsernameIgnoreCaseAndDeletedFalse}). Ohne diesen Guard könnten zwei nur in
 * der Schreibweise abweichende Namen ("Anna"/"anna") entstehen, an denen anschließend BEIDE
 * Logins dauerhaft mit {@code IncorrectResultSizeDataAccessException} scheitern. Der Guard
 * meldet den Konflikt stattdessen früh und sprechend (analog {@link DuplicateCardIdException}),
 * damit ihn der Benutzer-Dialog am Benutzername-Feld anzeigen kann.
 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("Der Benutzername '" + username + "' ist bereits vergeben (unabhängig von Groß-/Kleinschreibung).");
    }
}
