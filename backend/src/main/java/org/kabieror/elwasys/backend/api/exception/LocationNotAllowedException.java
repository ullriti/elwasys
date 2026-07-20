package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Entspricht dem Alt-Code-Zweig
 * {@code !location.getValidUserGroups().contains(newUser.getGroup())} in
 * {@code MainFormController#onCardDetected} ({@code visualizeLocationNotAllowed()}): die
 * Benutzergruppe ist am Standort des Terminals nicht zugelassen (siehe
 * {@link org.kabieror.elwasys.backend.service.PermissionService#isUserAllowedAtLocation}).
 */
public class LocationNotAllowedException extends ApiException {

    public LocationNotAllowedException(Integer userId, String locationName) {
        super(HttpStatus.FORBIDDEN, "location-not-allowed", "Standort nicht zugelassen",
                "Der Benutzer mit id=" + userId + " ist an diesem Standort ('" + locationName
                        + "') nicht zugelassen.");
    }
}
