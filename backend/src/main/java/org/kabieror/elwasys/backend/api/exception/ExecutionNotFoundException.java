package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Wird auch für eine existierende Ausführung an einem ANDEREN Standort geworfen (Standort-
 * Scope, siehe {@link DeviceNotFoundException}).
 */
public class ExecutionNotFoundException extends ApiException {

    public ExecutionNotFoundException(Integer executionId) {
        super(HttpStatus.NOT_FOUND, "execution-not-found", "Unbekannte Ausführung",
                "Es existiert keine Ausführung mit id=" + executionId + " am Standort dieses Terminal-Tokens.");
    }
}
