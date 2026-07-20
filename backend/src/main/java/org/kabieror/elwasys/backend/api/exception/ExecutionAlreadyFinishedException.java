package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

public class ExecutionAlreadyFinishedException extends ApiException {

    public ExecutionAlreadyFinishedException(Integer executionId) {
        super(HttpStatus.CONFLICT, "execution-already-finished", "Ausführung bereits abgeschlossen",
                "Die Ausführung mit id=" + executionId + " ist bereits abgeschlossen.");
    }
}
