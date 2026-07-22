package org.kabieror.elwasys.raspiclient.api;

import java.io.IOException;

/**
 * Wird von {@link ApiClient} für alle nicht-2xx-Antworten sowie echte Kommunikationsfehler
 * (Verbindungsaufbau/Timeout) geworfen (Phase 4 AP4, siehe docs/kb/05-migration-plan.md).
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
    private final boolean malformedResponse;

    public ApiException(String message) {
        super(message);
        this.httpStatus = 0;
        this.typeSlug = null;
        this.title = null;
        this.malformedResponse = false;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.typeSlug = null;
        this.title = null;
        this.malformedResponse = false;
    }

    public ApiException(int httpStatus, String typeSlug, String title, String detail) {
        super(detail != null && !detail.isBlank() ? detail : ("HTTP " + httpStatus));
        this.httpStatus = httpStatus;
        this.typeSlug = typeSlug;
        this.title = title;
        this.malformedResponse = false;
    }

    private ApiException(String message, Throwable cause, boolean malformedResponse) {
        super(message, cause);
        this.httpStatus = 0;
        this.typeSlug = null;
        this.title = null;
        this.malformedResponse = malformedResponse;
    }

    /**
     * Eine als 2xx (Erfolg) empfangene, aber inhaltlich unlesbare Antwort (z. B. kaputtes
     * JSON). Der Server war erreichbar und hat evtl. bereits gehandelt - dieser Fall darf
     * daher NICHT als Kommunikationsfehler (Offline-Auslöser) gelten, sondern ist ein echter
     * Serverfehler (Issue #53).
     */
    public static ApiException malformedResponse(String message, Throwable cause) {
        return new ApiException(message, cause, true);
    }

    /**
     * Der HTTP-Status der Antwort, oder {@code 0}, wenn keine Antwort empfangen wurde (reiner
     * Kommunikationsfehler).
     */
    public int getHttpStatus() {
        return this.httpStatus;
    }

    /**
     * Ob dies ein reiner Kommunikationsfehler ist (keine Antwort empfangen - Verbindung
     * verweigert/Timeout/DNS-Fehler o. ä.), im Unterschied zu einer empfangenen
     * Fehlerantwort (z. B. {@code 403 user-blocked}). Phase 4 AP6 (siehe
     * docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"): genau dieser
     * Fall löst die Offline-Behandlung aus ({@code offline.OfflineGateway}) - ein
     * echter fachlicher Fehler (z. B. gesperrter Benutzer) wird weiterhin ganz normal
     * gemeldet, auch wenn er lokal gegen den Snapshot geprüft wurde.
     */
    public boolean isCommunicationFailure() {
        // Eine kaputte 2xx-Antwort hat zwar keinen HTTP-Status (0), ist aber KEIN
        // Kommunikationsfehler - der Server war ja erreichbar (Issue #53).
        return this.httpStatus == 0 && !this.malformedResponse;
    }

    /**
     * Ob es sich um eine als Erfolg (2xx) empfangene, aber inhaltlich unlesbare Antwort
     * handelt (siehe {@link #malformedResponse(String, Throwable)}).
     */
    public boolean isMalformedResponse() {
        return this.malformedResponse;
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
