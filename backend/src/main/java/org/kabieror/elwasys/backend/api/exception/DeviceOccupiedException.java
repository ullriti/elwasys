package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Ein Gerät kann nur eine laufende Ausführung gleichzeitig haben (siehe
 * {@link org.kabieror.elwasys.backend.service.ExecutionService#getRunningExecution}, Alt-Code:
 * {@code Device#getCurrentExecution() != null} sperrt die Geräteauswahl in der UI).
 */
public class DeviceOccupiedException extends ApiException {

    public DeviceOccupiedException(Integer deviceId) {
        super(HttpStatus.CONFLICT, "device-occupied", "Gerät belegt",
                "Das Gerät mit id=" + deviceId + " hat bereits eine laufende Ausführung.");
    }
}
