package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

public class ProgramNotFoundException extends ApiException {

    public ProgramNotFoundException(Integer programId) {
        super(HttpStatus.NOT_FOUND, "program-not-found", "Unbekanntes Programm",
                "Es existiert kein Programm mit id=" + programId + ".");
    }
}
