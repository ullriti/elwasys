package org.kabieror.elwasys.backend.api;

import java.net.URI;
import org.kabieror.elwasys.backend.api.exception.ApiException;
import org.kabieror.elwasys.backend.exception.NotEnoughCreditException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Übersetzt alle fachlichen Terminal-API-Fehler ({@link ApiException} und Unterklassen, siehe
 * {@code org.kabieror.elwasys.backend.api.exception}) in RFC-7807-{@link ProblemDetail}
 * -Antworten (AP4, siehe kb/05-migration-plan.md: "Fehlerbilder konsistent (Problem-Details
 * o. ä.)"). Nur für Controller unter {@code org.kabieror.elwasys.backend.api} aktiv - das
 * künftige Vaadin-Flow-Portal (Phase 3) bekommt bei Bedarf eine eigene Fehlerbehandlung.
 *
 * <p>Bean-Validation-Fehler ({@code @Valid}) und andere Spring-MVC-Standardfehler werden
 * NICHT hier behandelt - Spring Boot liefert dafür bereits eigene {@link ProblemDetail}
 * -Antworten (siehe {@code spring.mvc.problemdetails.enabled=true} in
 * {@code application.yml}).
 */
@RestControllerAdvice(basePackages = "org.kabieror.elwasys.backend.api")
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
        problem.setType(URI.create("urn:elwasys:" + e.getTypeSlug()));
        problem.setTitle(e.getTitle());
        return problem;
    }

    /**
     * Verteidigungslinie: {@link NotEnoughCreditException} wird vom Terminal-API-Startpfad
     * ({@code ExecutionController#start}) proaktiv per
     * {@link org.kabieror.elwasys.backend.api.exception.InsufficientCreditException}
     * vermieden (siehe dort), aber {@code CreditService#payout} kann sie theoretisch auch
     * über andere, künftige Aufrufer werfen - hier ebenfalls auf 402 abgebildet, statt als
     * 500 durchzureichen.
     */
    @ExceptionHandler(NotEnoughCreditException.class)
    public ProblemDetail handleNotEnoughCredit(NotEnoughCreditException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, e.getMessage());
        problem.setType(URI.create("urn:elwasys:insufficient-credit"));
        problem.setTitle("Guthaben nicht ausreichend");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        LOG.error("Unexpected error while handling a terminal API request.", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ein unerwarteter Fehler ist aufgetreten.");
        problem.setType(URI.create("urn:elwasys:internal-error"));
        problem.setTitle("Interner Fehler");
        return problem;
    }
}
