package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Entspricht {@code org.kabieror.elwasys.common.Device#getPrograms(User)} im Alt-Code: das
 * Programm ist dem Gerät nicht zugeordnet oder für die Benutzergruppe nicht freigegeben
 * (siehe {@link org.kabieror.elwasys.backend.service.PermissionService#isProgramAvailableForDeviceAndUser}).
 */
public class ProgramNotAvailableException extends ApiException {

    public ProgramNotAvailableException(Integer programId, Integer deviceId, Integer userId) {
        super(HttpStatus.FORBIDDEN, "program-not-available", "Programm nicht verfügbar",
                "Das Programm mit id=" + programId + " ist auf Gerät id=" + deviceId
                        + " für den Benutzer mit id=" + userId + " nicht verfügbar.");
    }
}
