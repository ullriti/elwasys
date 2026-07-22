package org.kabieror.elwasys.backend.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration des Benachrichtigungsdienstes (AP5, siehe docs/kb/05-migration-plan.md).
 *
 * <p><b>{@code elwasys.notifications.enabled}</b> (Default: {@code false}): schaltet den
 * gesamten Dienst scharf. <b>Historisch kritisch (Doppelversand):</b> solange Client-Raspi
 * selbst E-Mails/Pushover-Nachrichten verschickte (der alte {@code ExecutionFinisher}-
 * Versandcode, per Phase 4 AP4 entfernt), durfte das Backend nicht zusätzlich versenden.
 * Seit Phase 4 AP4 ruft {@link org.kabieror.elwasys.backend.api.ExecutionController} bei
 * Programmende/-abbruch {@link NotificationService} tatsächlich auf - der Dienst ist also
 * produktiv verdrahtet, nur per Flag deaktiviert. Ob/wann das Flag produktiv aktiviert
 * wird, ist eine Entscheidung der eigentlichen Produktivumschaltung (Phase 6, siehe
 * docs/kb/05-migration-plan.md), nicht Teil eines einzelnen Arbeitspakets - der Code-Default
 * bleibt daher bewusst unverändert AUS. Analog zu {@code elwasys.auth.rehash-on-login}
 * (AP3, siehe {@code AuthProperties}).
 *
 * <p>SMTP-Zugangsdaten/-Transport selbst (Server/Port/Nutzer/Passwort/TLS) laufen über die
 * Standard-Spring-Boot-Properties {@code spring.mail.*} (siehe application.yml) statt einer
 * eigenen Konfigurationsklasse - das bringt die {@code JavaMailSender}-Autokonfiguration
 * kostenlos mit. Nur die Absenderadresse ({@code smtp.senderAddress} im Alt-
 * {@code ConfigurationManager}) hat hier kein Standard-Spring-Äquivalent (es gibt keine
 * globale "From"-Property) und wird daher unten separat geführt.
 */
@Component
@ConfigurationProperties(prefix = "elwasys.notifications")
public class NotificationsProperties {

    private boolean enabled = false;

    private final Smtp smtp = new Smtp();

    private final Pushover pushover = new Pushover();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Smtp getSmtp() {
        return this.smtp;
    }

    public Pushover getPushover() {
        return this.pushover;
    }

    public static class Smtp {

        /**
         * Entspricht {@code ConfigurationManager#getSmtpSenderAddress()} /
         * {@code smtp.senderAddress} im Alt-Code.
         */
        private String senderAddress = "";

        public String getSenderAddress() {
            return this.senderAddress;
        }

        public void setSenderAddress(String senderAddress) {
            this.senderAddress = senderAddress;
        }
    }

    public static class Pushover {

        /**
         * Entspricht {@code WashguardConfiguration#getPushoverApiToken()} im Alt-Code - dort
         * fest im Quellcode verdrahtet (nicht konfigurierbar). Abweichung hier: bewusst
         * konfigurierbar gemacht (Default leer) statt eines hartkodierten Secrets im
         * Quellcode - siehe docs/kb/05-migration-plan.md ("Abweichungen").
         */
        private String apiToken = "";

        /**
         * Ziel-URL der Pushover-API. Produktionsdefault entspricht 1:1
         * {@code net.pushover.client.PushoverRestClient#PUSH_MESSAGE_URL} im Alt-Code
         * ({@code pushover-client} 1.0.0, von {@code ExecutionFinisher} verwendet).
         * Überschreibbar für Tests (lokaler Mock-HTTP-Server statt der echten Pushover-API).
         */
        private String baseUrl = "https://api.pushover.net/1/messages.json";

        public String getApiToken() {
            return this.apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public String getBaseUrl() {
            return this.baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
