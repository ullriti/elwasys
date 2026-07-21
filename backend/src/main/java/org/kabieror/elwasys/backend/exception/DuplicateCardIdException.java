package org.kabieror.elwasys.backend.exception;

import org.kabieror.elwasys.backend.domain.UserEntity;

/**
 * Entspricht der Validierung in {@code Portal/.../components/UserWindow} (Alt-Portal,
 * {@code tfCardIds}-Validator): eine Kartennummer darf jeweils nur einem Benutzer
 * zugeordnet sein. Wird geworfen, wenn beim Anlegen/Bearbeiten eines Benutzers eine
 * Kartennummer angegeben wird, die bereits einem ANDEREN (nicht gelöschten) Benutzer
 * zugeordnet ist.
 */
public class DuplicateCardIdException extends RuntimeException {

    public DuplicateCardIdException(String cardId, UserEntity existingOwner) {
        super("Die Karte '" + cardId + "' ist bereits zu Benutzer " + existingOwner.getName() + " ("
                + existingOwner.getUsername() + ") zugeordnet.");
    }
}
