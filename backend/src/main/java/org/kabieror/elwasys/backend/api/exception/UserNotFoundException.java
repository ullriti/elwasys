package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(Integer userId) {
        super(HttpStatus.NOT_FOUND, "user-not-found", "Unbekannter Benutzer",
                "Es existiert kein Benutzer mit id=" + userId + ".");
    }
}
