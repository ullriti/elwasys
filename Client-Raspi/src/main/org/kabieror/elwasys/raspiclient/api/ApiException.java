package org.kabieror.elwasys.raspiclient.api;

import java.io.IOException;

/**
 * Wird von {@link ApiClient} für alle nicht-2xx-Antworten sowie echte Kommunikationsfehler
 * (Verbindungsaufbau/Timeout) geworfen (Phase 4 AP4, siehe kb/05-migration-plan.md).
 * <p>
 * Erbt bewusst von {@link IOException}: bestehender Aufrufer-Code, der bislang
 * {@code catch (IOException e)} für Kommunikationsfehler (z. B. mit fhem/deCONZ) verwendet
 * und dabei den Fehlertext "Kommunikationsfehler" anzeigt (siehe C15 im Testplan, jetzt
 * "Backend nicht erreichbar → ERROR" statt "DB nicht erreichbar → ERROR", gleiches
 * Fehlerbild), fängt eine {@link ApiException} damit unverändert ab, ohne dass jede
 * Aufrufstelle einen weiteren catch-Zweig braucht.
 * <p>
 * {@link #httpStatus} ist {@code 0}, wenn gar keine Antwort empfangen wurde (Verbindung
 * verweigert/Timeout/DNS-Fehler o. ä.) - {@link #typeSlug} ist dann immer {@code null}.
 * Für empfangene Fehlerantworten (RFC-7807 {@code ProblemDetail}, siehe
 * {@code ApiExceptionHandler} im Backend) enthält {@link #typeSlug} den Teil nach
 * {@code urn:elwasys:} aus dem {@code type}-Feld (z. B. {@code "user-blocked"}), damit
 * Aufrufer fachliche Fehlerfälle unterscheiden können (siehe Verwendung in
 * {@code MainFormController#onCardDetected}).
 */
public class ApiException extends IOException {

    private final int httpStatus;
    private final String typeSlug;
    private final String title;

    public ApiException(String message) {
        super(message);
        this.httpStatus = 0;
        this.typeSlug = null;
        this.title = null;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.typeSlug = null;
        this.title = null;
    }

    public ApiException(int httpStatus, String typeSlug, String title, String detail) {
        super(detail != null && !detail.isBlank() ? detail : ("HTTP " + httpStatus));
        this.httpStatus = httpStatus;
        this.typeSlug = typeSlug;
        this.title = title;
    }

    /**
     * Der HTTP-Status der Antwort, oder {@code 0}, wenn keine Antwort empfangen wurde (reiner
     * Kommunikationsfehler).
     */
    public int getHttpStatus() {
        return this.httpStatus;
    }

    /**
     * Der fachliche Fehler-Slug aus der {@code ProblemDetail}-Antwort (z. B.
     * {@code "user-blocked"}), oder {@code null} bei einem reinen Kommunikationsfehler bzw.
     * einer Antwort ohne auswertbaren {@code type}.
     */
    public String getTypeSlug() {
        return this.typeSlug;
    }

    /**
     * Prüft, ob diese Ausnahme den gegebenen HTTP-Status UND fachlichen Fehler-Slug trägt -
     * bequeme Kurzform für die häufige Fallunterscheidung an den Aufrufstellen.
     */
    public boolean is(int expectedStatus, String expectedTypeSlug) {
        return this.httpStatus == expectedStatus && expectedTypeSlug.equals(this.typeSlug);
    }

    public String getTitle() {
        return this.title;
    }
}
