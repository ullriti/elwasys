package org.kabieror.elwasys.backend.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration des Passwort-Reset-per-Email-Flows (Phase 3 AP4, siehe
 * docs/kb/05-migration-plan.md). Fachlicher Nachfolger von {@code PasswordForgotWindow}/
 * {@code ResetPasswordWindow}/{@code UserWindow}s Admin-Passwort-Reset (Alt-Portal).
 *
 * <p><b>{@code elwasys.password-reset.enabled}</b> (Default: {@code true}) - bewusst ANDERS
 * als {@code elwasys.notifications.enabled}/{@code elwasys.auth.rehash-on-login} (beide
 * Default AUS wegen Parallelbetriebs-Risiken, siehe deren Javadoc): dieser Schalter schützt
 * NICHT vor einem Doppelversand, weil kein Alt-Code-Pfad automatisch auf ein Backend-Ereignis
 * reagiert - Passwort-Reset-Mails werden ausschließlich durch eine explizite, interaktive
 * Aktion einer Portal-Session ausgelöst (Nutzer klickt "Passwort vergessen?" bzw. ein Admin
 * setzt im Benutzer-Dialog ein neues Passwort). Nutzt ein/e Admin/Nutzer statt dessen (oder
 * zusätzlich) weiterhin das Alt-Portal für denselben Vorgang, verschickt jeder Portal-Pfad für
 * sich genau eine Mail für seine eigene Aktion - keine zwei Systeme reagieren auf dasselbe
 * Ereignis. Der Schalter existiert trotzdem als eigenständige Ops-Bremse (z.B. um das Backend
 * ohne konfigurierten SMTP-Server sauber laufen zu lassen, ohne Fehler in den Logs zu
 * erzeugen) - siehe docs/kb/05-migration-plan.md, "Entscheidungen" für die vollständige
 * Begründung.
 */
@Component
@ConfigurationProperties(prefix = "elwasys.password-reset")
public class PasswordResetProperties {

    private boolean enabled = true;

    /**
     * Gültigkeitsdauer eines Reset-Schlüssels - 1:1 wie
     * {@code common.User#generatePasswordResetKey} (dort hartkodiert 2 Stunden).
     */
    private Duration tokenValidity = Duration.ofHours(2);

    /**
     * Basis-URL des neuen Portals für den Link in der Reset-Email (z.B.
     * {@code https://waschportal.example.org}, ohne abschließenden Schrägstrich). Anders als
     * im Alt-Code ({@code WashportalUtilities#getPasswordResetUrl}, das die URL aus der
     * aktuellen Browser-Anfrage ableitet) bewusst konfigurierbar gemacht - robuster, wenn die
     * Mail nicht zwingend im Kontext einer laufenden HTTP-Anfrage verschickt wird (z.B. hinter
     * einem Reverse Proxy mit abweichendem Host).
     */
    private String portalBaseUrl = "http://localhost:8080";

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTokenValidity() {
        return this.tokenValidity;
    }

    public void setTokenValidity(Duration tokenValidity) {
        this.tokenValidity = tokenValidity;
    }

    public String getPortalBaseUrl() {
        return this.portalBaseUrl;
    }

    public void setPortalBaseUrl(String portalBaseUrl) {
        this.portalBaseUrl = portalBaseUrl;
    }
}
