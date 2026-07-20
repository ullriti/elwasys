package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Entspricht dem Alt-Code-Zweig {@code newUser.isBlocked()} in
 * {@code MainFormController#onCardDetected} ({@code visualizeBlockedUser()}): ein gesperrter
 * Benutzer wird beim Kartenlogin abgewiesen.
 */
public class UserBlockedException extends ApiException {

    public UserBlockedException(Integer userId) {
        super(HttpStatus.FORBIDDEN, "user-blocked", "Benutzer gesperrt",
                "Der Benutzer mit id=" + userId + " ist gesperrt.");
    }
}
