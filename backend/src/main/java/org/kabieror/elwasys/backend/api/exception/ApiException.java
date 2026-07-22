package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Basisklasse aller fachlichen Fehler der Terminal-REST-API ({@code /api/v1/**}, AP4, siehe
 * docs/kb/05-migration-plan.md). {@link org.kabieror.elwasys.backend.api.ApiExceptionHandler}
 * übersetzt jede Instanz in eine RFC-7807-{@link org.springframework.http.ProblemDetail}
 * -Antwort mit dem hier festgelegten HTTP-Status, Typ-URI und Titel.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    private final String typeSlug;

    private final String title;

    protected ApiException(HttpStatus status, String typeSlug, String title, String detail) {
        super(detail);
        this.status = status;
        this.typeSlug = typeSlug;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return this.status;
    }

    public String getTypeSlug() {
        return this.typeSlug;
    }

    public String getTitle() {
        return this.title;
    }
}
