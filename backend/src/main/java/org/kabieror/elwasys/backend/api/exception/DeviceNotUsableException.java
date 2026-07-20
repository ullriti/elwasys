package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Entspricht {@code DeviceListEntry#applyUserStyle} im Alt-Code: das Gerät ist deaktiviert
 * oder die Benutzergruppe ist am Gerät nicht zugelassen (siehe
 * {@link org.kabieror.elwasys.backend.service.PermissionService#isDeviceUsableByUser}).
 */
public class DeviceNotUsableException extends ApiException {

    public DeviceNotUsableException(Integer deviceId, Integer userId) {
        super(HttpStatus.FORBIDDEN, "device-not-usable", "Gerät nicht nutzbar",
                "Das Gerät mit id=" + deviceId + " ist deaktiviert oder für den Benutzer mit id=" + userId
                        + " nicht freigegeben.");
    }
}
