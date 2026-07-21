package org.kabieror.elwasys.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.auth.PasswordResetProperties;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Pushover-Kanal des Benachrichtigungsdienstes (AP5, siehe kb/05-migration-plan.md) gegen
 * einen eingebetteten JDK-{@code HttpServer} als Mock (statt der echten Pushover-API).
 *
 * <p>Erwartete Anfrage 1:1 aus dem Bytecode von {@code net.pushover.client
 * .PushoverRestClient#pushMessage} hergeleitet (siehe {@link PushoverClient}-Javadoc für die
 * vollständige Herleitung): POST auf {@code /1/messages.json} mit Form-URL-encoded-Body
 * {@code token, user, message, title, url, url_title, priority} - der Alt-Aufruf in
 * {@code ExecutionFinisher} setzt {@code url="http://waschportal.hilaren.de"},
 * {@code url_title="Waschportal"} und {@code priority=MessagePriority.HIGH} (dessen
 * {@code toString()} liefert {@code "1"}), lässt {@code device}/{@code timestamp}/
 * {@code sound} weg (null -> vom Alt-Client ausgelassen).
 */
class NotificationServicePushoverTest {

    private HttpServer server;

    private final BlockingQueue<RecordedRequest> requests = new ArrayBlockingQueue<>(10);

    private NotificationsProperties properties;

    private NotificationService service;

    private record RecordedRequest(String method, String path, String contentType, Map<String, String> form) {
    }

    @BeforeEach
    void setUp() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/1/messages.json", this::handle);
        this.server.start();

        this.properties = new NotificationsProperties();
        this.properties.setEnabled(true);
        this.properties.getPushover().setApiToken("test-api-token");
        this.properties.getPushover()
                .setBaseUrl("http://localhost:" + this.server.getAddress().getPort() + "/1/messages.json");

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        this.service = new NotificationService(this.properties, mailSender, new PushoverClient(this.properties),
                new PasswordResetProperties());
    }

    @AfterEach
    void tearDown() {
        this.server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        this.requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                contentType, form));

        byte[] response = "{\"status\":1,\"request\":\"test-request-id\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static UserEntity userWithPushoverKey(String key) {
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        UserEntity user = new UserEntity("Erika Mustermann", "erika", group);
        user.setEmailNotification(false);
        user.setPushoverUserKey(key);
        return user;
    }

    private static DeviceEntity device(String name) {
        return new DeviceEntity(name, 0, new LocationEntity("Waschkeller"));
    }

    @Test
    void executionFinishedSendsExactAltCodeFormRequest() throws Exception {
        UserEntity user = userWithPushoverKey("uQiRzpo4DXghDmr9QzzfQu27cmVRsG");

        this.service.notifyExecutionFinished(user, device("Waschmaschine 1"));

        RecordedRequest request = this.requests.poll(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/1/messages.json");
        assertThat(request.contentType()).startsWith("application/x-www-form-urlencoded");

        Map<String, String> form = request.form();
        assertThat(form).containsEntry("token", "test-api-token");
        assertThat(form).containsEntry("user", "uQiRzpo4DXghDmr9QzzfQu27cmVRsG");
        // notificationMessageShort (Alt-Code): device.getName() + " ist fertig. Bitte entferne die Wäsche umgehend."
        assertThat(form).containsEntry("message", "Waschmaschine 1 ist fertig. Bitte entferne die Wäsche umgehend.");
        // notificationTitle (Alt-Code): device.getName() + " ist fertig!"
        assertThat(form).containsEntry("title", "Waschmaschine 1 ist fertig!");
        assertThat(form).containsEntry("url", "http://waschportal.hilaren.de");
        assertThat(form).containsEntry("url_title", "Waschportal");
        assertThat(form).containsEntry("priority", "1");
        assertThat(form).doesNotContainKey("device");
        assertThat(form).doesNotContainKey("timestamp");
        assertThat(form).doesNotContainKey("sound");
    }

    @Test
    void executionAbortedSendsExactAltCodeShortMessage() throws Exception {
        UserEntity user = userWithPushoverKey("uQiRzpo4DXghDmr9QzzfQu27cmVRsG");

        this.service.notifyExecutionAborted(user, device("Waschmaschine 2"));

        RecordedRequest request = this.requests.poll(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        // notificationMessageShort (Alt-Code): "Der Waschvorgang auf " + device.getName() + " wurde abgebrochen."
        assertThat(request.form()).containsEntry("message", "Der Waschvorgang auf Waschmaschine 2 wurde abgebrochen.");
        assertThat(request.form()).containsEntry("title", "Waschvorgang abgebrochen!");
    }

    @Test
    void userWithoutPushoverKeyReceivesNoPush() throws Exception {
        UserEntity user = userWithPushoverKey("");

        this.service.notifyExecutionFinished(user, device("Waschmaschine 3"));

        assertThat(this.requests.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void disabledServiceSendsNoPushEvenWithOptIn() throws Exception {
        this.properties.setEnabled(false);
        UserEntity user = userWithPushoverKey("uQiRzpo4DXghDmr9QzzfQu27cmVRsG");

        this.service.notifyExecutionFinished(user, device("Waschmaschine 4"));

        assertThat(this.requests.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void pushNotificationOptInColumnDoesNotGatePushover() throws Exception {
        // Regressionsschutz für die im NotificationService-Javadoc dokumentierte Falle:
        // users.push_notification (UserEntity#isPushNotification) ist im Alt-Code das
        // elwaApp/Ionic-Opt-in, NICHT das Pushover-Opt-in. Auch mit pushNotification=false
        // muss Pushover weiterhin ausgelöst werden, solange ein pushoverUserKey gesetzt ist.
        UserEntity user = userWithPushoverKey("uQiRzpo4DXghDmr9QzzfQu27cmVRsG");
        user.setPushNotification(false);

        this.service.notifyExecutionFinished(user, device("Waschmaschine 5"));

        RecordedRequest request = this.requests.poll(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
    }
}
