package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Wird sowohl für ein tatsächlich unbekanntes Gerät als auch für ein Gerät eines ANDEREN
 * Standorts geworfen (Standort-Scope, siehe kb/05-migration-plan.md, AP4: "ein Token sieht
 * nur Geräte/Executions seines Standorts"; vgl. Client-E2E-Fall C16, in dem ein
 * standortfremdes Gerät schlicht nicht in der Liste erscheint). Bewusst kein {@code 403} für
 * den Scope-Fall: das würde die Existenz des Geräts an einem fremden Standort verraten.
 */
public class DeviceNotFoundException extends ApiException {

    public DeviceNotFoundException(Integer deviceId) {
        super(HttpStatus.NOT_FOUND, "device-not-found", "Unbekanntes Gerät",
                "Es existiert kein Gerät mit id=" + deviceId + " am Standort dieses Terminal-Tokens.");
    }
}
