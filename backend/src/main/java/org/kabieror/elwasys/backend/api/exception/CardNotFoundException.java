package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Entspricht dem Alt-Code-Zweig "Kein Benutzer zur Id gefunden"
 * ({@code MainFormController#onCardDetected}, Kartennummer ohne zugeordneten Benutzer).
 */
public class CardNotFoundException extends ApiException {

    public CardNotFoundException(String cardId) {
        super(HttpStatus.NOT_FOUND, "card-not-found", "Unbekannte Kartennummer",
                "Es ist kein Benutzer mit der Kartennummer '" + cardId + "' registriert.");
    }
}
