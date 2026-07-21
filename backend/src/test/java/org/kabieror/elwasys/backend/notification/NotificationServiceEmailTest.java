package org.kabieror.elwasys.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.auth.PasswordResetProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * E-Mail-Kanal des Benachrichtigungsdienstes (AP5, siehe kb/05-migration-plan.md) gegen
 * einen echten lokalen Test-SMTP-Server (GreenMail, siehe backend/pom.xml).
 *
 * <p>Betreff/Body sind wörtliche Zitate aus {@code ExecutionFinisher#executeAction()} im
 * Alt-Code (Client-Raspi), siehe die dortigen {@code notificationTitle}/
 * {@code notificationMessageLong}-Zuweisungen - ein isoliertes Aufrufen des Alt-Codes
 * selbst ist hier nicht praktikabel, weil {@code Utilities#sendEmail(String, String, User)}
 * ein {@code org.kabieror.elwasys.common.User} mit gesetzter {@code email} erwartet, die
 * sich ohne eine echte {@code DataManager}/DB-Anbindung nicht erzeugen lässt (der einzige
 * DB-lose Konstruktor, {@code User.getTestUser(String)}, liefert eine leere E-Mail-Adresse
 * und {@code emailNotification=false}). Die SHA1-Parität in
 * {@code PasswordVerificationServiceParityTest} (AP3) konnte den echten Alt-Code deshalb
 * aufrufen, hier zitieren wir stattdessen die Erwartungswerte direkt aus dem Quelltext, wie
 * im Auftrag als Alternative vorgesehen.
 */
class NotificationServiceEmailTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private NotificationsProperties properties;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        this.properties = new NotificationsProperties();
        this.properties.setEnabled(true);
        this.properties.getSmtp().setSenderAddress("elwasys@example.com");

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(ServerSetupTest.SMTP.getPort());
        mailSender.setDefaultEncoding("UTF-8");

        this.service = new NotificationService(this.properties, mailSender, new PushoverClient(this.properties),
                new PasswordResetProperties());
    }

    private static UserEntity userWithEmail(String email) {
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        UserEntity user = new UserEntity("Erika Mustermann", "erika", group);
        user.setEmail(email);
        user.setEmailNotification(true);
        return user;
    }

    private static DeviceEntity device(String name) {
        return new DeviceEntity(name, 0, new LocationEntity("Waschkeller"));
    }

    /**
     * {@code MimeMessage#getContent()} statt {@code GreenMailUtil#getBody(...)}: Letzteres
     * liefert den rohen (Quoted-Printable-kodierten) Textkörper, weil Umlaute/das
     * Uhrzeit-Formatzeichen (schmales geschütztes Leerzeichen vor "PM"/"AM" je nach
     * JVM-Locale) eine non-ASCII-Transfer-Encoding auslösen - {@code getContent()} dekodiert
     * korrekt. MIME normalisiert Zeilenumbrüche auf {@code \r\n} (RFC 2045); für den
     * Vergleich mit den (mit {@code \n} geschriebenen) Alt-Code-Zitaten wird das wieder auf
     * {@code \n} zurückgeführt.
     */
    private static String decodedBody(MimeMessage message) throws Exception {
        return ((String) message.getContent()).replace("\r\n", "\n");
    }

    @Test
    void executionFinishedSendsExactAltCodeSubjectAndBody() throws Exception {
        UserEntity user = userWithEmail("erika@example.com");
        DeviceEntity device = device("Waschmaschine 1");

        this.service.notifyExecutionFinished(user, device);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage message = received[0];

        // Alt-Code (ExecutionFinisher#executeAction, Zweig "!aborted"):
        // notificationTitle = device.getName() + " ist fertig!";
        assertThat(message.getSubject()).isEqualTo("Waschmaschine 1 ist fertig!");
        assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("erika@example.com");
        assertThat(message.getFrom()).extracting(Object::toString).containsExactly("elwasys@example.com");

        // notificationMessageLong = "Hallo " + user.getName() + ",\n\n" + device.getName() +
        //         " ist gerade fertig.\n" + "Uhrzeit: " + <jetzt, lokalisiert kurz> +
        //         "\n" + "Bitte entferne die Wäsche umgehend.\n\n--\nelwasys";
        String body = decodedBody(message);
        assertThat(body).startsWith("Hallo Erika Mustermann,\n\nWaschmaschine 1 ist gerade fertig.\nUhrzeit: ");
        assertThat(body).endsWith("\nBitte entferne die Wäsche umgehend.\n\n--\nelwasys");
    }

    @Test
    void executionAbortedSendsExactAltCodeSubjectAndBody() throws Exception {
        UserEntity user = userWithEmail("erika@example.com");
        DeviceEntity device = device("Waschmaschine 2");

        this.service.notifyExecutionAborted(user, device);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        MimeMessage message = received[0];

        // Alt-Code (ExecutionFinisher#executeAction, Zweig "aborted"):
        // notificationTitle = "Waschvorgang abgebrochen!";
        assertThat(message.getSubject()).isEqualTo("Waschvorgang abgebrochen!");

        // notificationMessageLong = "Hallo " + user.getName() + ",\n\n dein Waschvorgang auf " +
        //         device.getName() + " wurde gerade abgebrochen.\n" + "Uhrzeit: " +
        //         <jetzt, lokalisiert kurz> + "\n\n--\nelwasys";
        String body = decodedBody(message);
        assertThat(body)
                .startsWith("Hallo Erika Mustermann,\n\n dein Waschvorgang auf Waschmaschine 2 wurde gerade "
                        + "abgebrochen.\nUhrzeit: ");
        assertThat(body).endsWith("\n\n--\nelwasys");
    }

    @Test
    void userWithoutEmailOptInReceivesNoMail() {
        UserEntity user = userWithEmail("erika@example.com");
        user.setEmailNotification(false);

        this.service.notifyExecutionFinished(user, device("Waschmaschine 3"));

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void disabledServiceSendsNothingEvenWithOptIn() {
        this.properties.setEnabled(false);
        UserEntity user = userWithEmail("erika@example.com");

        this.service.notifyExecutionFinished(user, device("Waschmaschine 4"));

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void sendFailureIsLoggedButDoesNotThrow() {
        // E-Mail-Opt-in an, aber keine Adresse gesetzt - entspricht dem Alt-Code, der
        // getEmailNotification() prüft, aber nicht zusätzlich, ob eine Adresse gesetzt ist
        // (siehe Klassen-Javadoc von NotificationService). Der resultierende Versandfehler
        // darf nicht nach außen dringen (siehe dortige "Abweichungen").
        UserEntity user = userWithEmail(null);

        this.service.notifyExecutionFinished(user, device("Waschmaschine 5"));

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }
}
