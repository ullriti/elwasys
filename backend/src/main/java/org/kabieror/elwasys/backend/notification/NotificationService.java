package org.kabieror.elwasys.backend.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import org.kabieror.elwasys.backend.auth.PasswordResetProperties;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Benachrichtigungsdienst (AP5, siehe kb/05-migration-plan.md): 1:1-Portierung der
 * Benachrichtigungslogik aus {@code ExecutionFinisher#executeAction()} im Client-Alt-Code
 * (Kanäle E-Mail und Pushover; der dritte Alt-Kanal, eine Push-Benachrichtigung an die
 * elwaApp über die Ionic-API, ist bewusst NICHT portiert - siehe Klassen-Javadoc unten,
 * "Abweichungen").
 *
 * <p><b>Scharfschaltung (kritisch):</b> diese Klasse wird von KEINEM produktiven Ablauf
 * aufgerufen. Solange Client-Raspi im Parallelbetrieb weiterhin selbst Benachrichtigungen
 * verschickt (Phase 2-4), würde ein zusätzlicher Versand durch das Backend zu
 * Doppelversand führen. Die eigentliche Verdrahtung mit echten Ereignissen (Terminal meldet
 * "Programm beendet"/"abgebrochen" über die API) kommt in Phase 4 - siehe
 * kb/05-migration-plan.md, "Entscheidungen". Zusätzlich schützt
 * {@link NotificationsProperties#isEnabled()} (Default {@code false}): ist der Dienst
 * deaktiviert, wird jeder Aufruf geloggt und ignoriert (siehe {@link #dispatch}).
 *
 * <p><b>Empfängerermittlung/Opt-in</b> (1:1 aus {@code User}/{@code ExecutionFinisher} im
 * Alt-Code):
 * <ul>
 *   <li>E-Mail: nur wenn {@code user.getEmailNotification()} (Spalte
 *       {@code users.email_notification}) {@code true} ist. Der Alt-Code prüft NICHT
 *       zusätzlich, ob {@code email} gesetzt ist - wird hier bewusst genauso
 *       nachgebildet (ein leeres/fehlendes E-Mail-Feld führt zu einem Versandfehler, der
 *       wie jeder andere abgefangen und geloggt wird, siehe {@link #sendEmail}).</li>
 *   <li>Pushover: nur wenn {@code user.getPushoverUserKey()} nicht {@code null} und nicht
 *       leer ist (Spalte {@code users.pushover_user_key}). <b>Wichtig:</b> die Spalte
 *       {@code users.push_notification} (Entity-Feld {@code UserEntity#isPushNotification()})
 *       ist im Alt-Code NICHT das Pushover-Opt-in, sondern das Opt-in für den dritten,
 *       hier nicht portierten Kanal (elwaApp/Ionic-Push, siehe {@code User#isPushEnabled()}
 *       im Alt-Code, das denselben DB-Wert liest). Diese Klasse liest
 *       {@code push_notification} daher bewusst NICHT.</li>
 * </ul>
 *
 * <p><b>Abweichungen vom Alt-Code:</b>
 * <ul>
 *   <li>Der dritte Alt-Kanal (elwaApp-Push über {@code https://api.ionic.io/push/notifications})
 *       ist NICHT portiert: die mobile App ({@code elwaapi}) wurde laut Auftraggeber als
 *       nicht mehr relevant eingestuft und alle zugehörigen Reste (u.a. {@code app_id},
 *       auf das dieser Kanal aufbaut) wurden in Phase 5 AP4 (V10) entfernt (siehe
 *       kb/05-migration-plan.md, Entscheidungen "Mobile App (`elwaapi`) ist nicht
 *       relevant"). Dieses Arbeitspaket ist zudem explizit auf "SMTP + Pushover"
 *       zugeschnitten (siehe Auftrag).</li>
 *   <li>Die Passwort-Zurücksetzen-/Neues-Passwort-E-Mails aus dem Alt-Portal
 *       ({@code PasswordForgotWindow}, {@code UserWindow}) sind ebenfalls NICHT Teil
 *       dieses Arbeitspakets - sie hängen am (noch nicht existierenden) neuen Portal-Login-
 *       Flow und sind in der Roadmap explizit Phase 3 zugeordnet ("Passwort ändern/
 *       zurücksetzen (E-Mail-Flow)", siehe kb/05-migration-plan.md, Phase 3). Beide
 *       Trigger sind in kb/05-migration-plan.md (AP5-Abschnitt) inventarisiert.</li>
 *   <li>Der No-Op-Zeichensatz-Roundtrip des Alt-Codes ({@code new String(x.getBytes(),
 *       Charset.defaultCharset())}) wird nicht nachgebildet - er ist unter einem
 *       durchgängigen Zeichensatz wirkungslos; das Backend verwendet stattdessen konsequent
 *       UTF-8 (siehe application.yml, {@code spring.mail.default-encoding}), was
 *       plattformabhängiges Verhalten sogar vermeidet statt es zu riskieren.</li>
 *   <li>Fehlerbehandlung beim Versand ist bewusst breiter als im Alt-Code (dort
 *       {@code EmailException}/{@code PushoverException}, hier {@code Exception}): Spring
 *       Mail/{@code java.net.http} können andere Exception-Typen werfen als
 *       commons-mail/die {@code pushover-client}-Bibliothek. Das Verhalten - ein
 *       Versandfehler bricht den fachlichen Ablauf NICHT ab, sondern wird nur geloggt -
 *       bleibt dasselbe, nur robuster gegen abweichende Exception-Hierarchien.</li>
 * </ul>
 */
@Service
public class NotificationService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final NotificationsProperties properties;

    private final JavaMailSender mailSender;

    private final PushoverClient pushoverClient;

    private final PasswordResetProperties passwordResetProperties;

    public NotificationService(NotificationsProperties properties, JavaMailSender mailSender,
            PushoverClient pushoverClient, PasswordResetProperties passwordResetProperties) {
        this.properties = properties;
        this.mailSender = mailSender;
        this.pushoverClient = pushoverClient;
        this.passwordResetProperties = passwordResetProperties;
    }

    /**
     * 1:1-Portierung des "regulären Ende"-Zweigs von
     * {@code ExecutionFinisher#executeAction()} (Alt-Code, {@code this.aborted == false}):
     * <pre>
     * notificationTitle = device.getName() + " ist fertig!";
     * notificationMessageShort = device.getName() + " ist fertig. Bitte entferne die Wäsche umgehend.";
     * notificationMessageLong = "Hallo " + user.getName() + ",\n\n" + device.getName() +
     *         " ist gerade fertig.\n" + "Uhrzeit: " + &lt;jetzt, lokalisiert kurz&gt; +
     *         "\n" + "Bitte entferne die Wäsche umgehend.\n\n--\nelwasys";
     * </pre>
     */
    public void notifyExecutionFinished(UserEntity user, DeviceEntity device) {
        String deviceName = device.getName();
        String title = deviceName + " ist fertig!";
        String shortMessage = deviceName + " ist fertig. Bitte entferne die Wäsche umgehend.";
        String longMessage = "Hallo " + user.getName() + ",\n\n" + deviceName + " ist gerade fertig.\n"
                + "Uhrzeit: " + formattedNow() + "\n" + "Bitte entferne die Wäsche umgehend.\n\n--\nelwasys";
        dispatch(user, title, shortMessage, longMessage);
    }

    /**
     * 1:1-Portierung des Abbruch-Zweigs von {@code ExecutionFinisher#executeAction()}
     * (Alt-Code, {@code this.aborted == true}):
     * <pre>
     * notificationTitle = "Waschvorgang abgebrochen!";
     * notificationMessageShort = "Der Waschvorgang auf " + device.getName() + " wurde abgebrochen.";
     * notificationMessageLong = "Hallo " + user.getName() + ",\n\n dein Waschvorgang auf " +
     *         device.getName() + " wurde gerade abgebrochen.\n" + "Uhrzeit: " +
     *         &lt;jetzt, lokalisiert kurz&gt; + "\n\n--\nelwasys";
     * </pre>
     * (Das führende Leerzeichen vor "dein" nach dem doppelten Zeilenumbruch steht so im
     * Alt-Code und wird hier bewusst 1:1 übernommen, siehe Auftrag "exakt übernehmen".)
     */
    public void notifyExecutionAborted(UserEntity user, DeviceEntity device) {
        String deviceName = device.getName();
        String title = "Waschvorgang abgebrochen!";
        String shortMessage = "Der Waschvorgang auf " + deviceName + " wurde abgebrochen.";
        String longMessage = "Hallo " + user.getName() + ",\n\n dein Waschvorgang auf " + deviceName
                + " wurde gerade abgebrochen.\n" + "Uhrzeit: " + formattedNow() + "\n\n--\nelwasys";
        dispatch(user, title, shortMessage, longMessage);
    }

    /**
     * 1:1 wie {@code LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}
     * im Alt-Code - übernimmt bewusst auch dessen Abhängigkeit von der JVM-Standard-Locale
     * (kein hartkodiertes {@code Locale.GERMANY}), damit das Format in Produktion
     * unverändert bleibt.
     */
    private String formattedNow() {
        return LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
    }

    private void dispatch(UserEntity user, String title, String shortMessage, String longMessage) {
        if (!this.properties.isEnabled()) {
            this.logger.debug("Benachrichtigungsdienst deaktiviert (elwasys.notifications.enabled=false) - "
                    + "Ereignis fuer Benutzer '{}' wird ignoriert.", user.getUsername());
            return;
        }

        if (user.isEmailNotification()) {
            sendEmail(user, title, longMessage);
        } else {
            this.logger.debug("Benutzer '{}' moechte keine Email-Benachrichtigung.", user.getUsername());
        }

        String pushoverUserKey = user.getPushoverUserKey();
        if (pushoverUserKey != null && !pushoverUserKey.isEmpty()) {
            sendPushover(pushoverUserKey, title, shortMessage);
        }
    }

    /**
     * 1:1-Portierung von {@code Utilities#sendEmail(String, String, User)} (Alt-Code):
     * einfache E-Mail mit Betreff/Text an genau einen Empfänger, Absender aus der
     * Konfiguration. Ein Versandfehler wird geloggt, aber nicht weitergeworfen (siehe
     * Alt-Code: {@code catch (EmailException e1) { logger.error(...); }} in
     * {@code ExecutionFinisher}).
     */
    private void sendEmail(UserEntity user, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(content);
            message.setFrom(this.properties.getSmtp().getSenderAddress());
            this.mailSender.send(message);
            this.logger.debug("Sent notification to {}", user.getEmail());
        } catch (Exception e) {
            this.logger.error("Could not send the notification mail.", e);
        }
    }

    /**
     * 1:1-Portierung des Pushover-Zweigs von {@code ExecutionFinisher#executeAction()}. Ein
     * Versandfehler wird geloggt, aber nicht weitergeworfen (siehe Alt-Code:
     * {@code catch (PushoverException e1) { logger.error(...); }}).
     */
    private void sendPushover(String userKey, String title, String message) {
        try {
            PushoverClient.Result result = this.pushoverClient.sendMessage(userKey, title, message);
            this.logger.debug("Sent push notification. Status: {}", result.pushoverStatus());
        } catch (Exception e) {
            this.logger.error("Could not send push notification.", e);
        }
    }

    /**
     * 1:1-Portierung von {@code PasswordForgotWindow#execute} (Alt-Portal, Testfall P19,
     * Phase 3 AP4 - siehe {@code org.kabieror.elwasys.backend.service.PasswordResetService}):
     * Betreff/Text wortgleich zum Alt-Code. Anders als {@link #dispatch} (Ausführungs-
     * Benachrichtigungen, Fehler werden geloggt und geschluckt) wirft diese Methode einen
     * Versandfehler WEITER - der Alt-Code zeigt bei einer {@code EmailException} ebenfalls
     * einen Fehler im Dialog an, statt ihn stillschweigend zu verschlucken. Ob überhaupt
     * versucht wird zu versenden, steuert
     * {@link org.kabieror.elwasys.backend.auth.PasswordResetProperties#isEnabled()} (eigener
     * Schalter, NICHT {@link NotificationsProperties#isEnabled()} - siehe dessen Javadoc für
     * die Begründung des separaten Schalters).
     */
    public void sendPasswordResetEmail(UserEntity user, String resetUrl) {
        if (!this.passwordResetProperties.isEnabled()) {
            this.logger.debug("Passwort-Reset-Mailversand deaktiviert (elwasys.password-reset.enabled=false) - "
                    + "Reset-Anfrage fuer Benutzer '{}' wird ignoriert.", user.getUsername());
            return;
        }
        String message = "Hallo " + user.getName() + ",\n\n"
                + "bitte besuche die folgende Webseite zum Setzen eines neuen Passworts.\n" + resetUrl
                + "\n\n--\nWaschportal";
        sendEmailOrThrow(user, "Passwort zurücksetzen", message);
    }

    /**
     * 1:1-Portierung von {@code UserWindow#save} (Zweig {@code cbSendPassword}, Alt-Portal):
     * Betreff/Text wortgleich zum Alt-Code. Siehe {@link #sendPasswordResetEmail} für die
     * Fehlerbehandlungs-/Schalter-Semantik.
     */
    public void sendNewPasswordEmail(UserEntity user, String newPassword) {
        if (!this.passwordResetProperties.isEnabled()) {
            this.logger.debug("Passwort-Reset-Mailversand deaktiviert (elwasys.password-reset.enabled=false) - "
                    + "Admin-Passwort-Reset fuer Benutzer '{}' wird ignoriert.", user.getUsername());
            return;
        }
        String message = "Hallo " + user.getName() + ",\n\n"
                + "hier ist dein neues Passwort für das Waschportal: " + newPassword + "\n"
                + "Zusammen mit deinem Benutzernamen '" + user.getUsername()
                + "' kannst du dich jetzt einloggen und dort dein Guthaben und abgebuchte Waschvorgänge ansehen.\n\n"
                + "--\nWaschportal";
        sendEmailOrThrow(user, "Waschportal - Neues Passwort", message);
    }

    /**
     * Anders als {@link #sendEmail} (schluckt Fehler): wirft eine unchecked Exception weiter,
     * damit der aufrufende Dialog (analog zum Alt-Code) einen Fehler anzeigen kann.
     */
    private void sendEmailOrThrow(UserEntity user, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(content);
        message.setFrom(this.properties.getSmtp().getSenderAddress());
        this.mailSender.send(message);
        this.logger.debug("Sent password reset/new-password mail to {}", user.getEmail());
    }
}
