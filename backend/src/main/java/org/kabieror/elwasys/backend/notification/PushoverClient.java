package org.kabieror.elwasys.backend.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * HTTP-Client für die Pushover-Nachrichten-API (<a href="https://pushover.net/api">
 * pushover.net/api</a>), 1:1-Nachbildung der vom Alt-Code über die Bibliothek
 * {@code net.pushover.client} (Maven-Koordinate {@code com.github.sps.pushover.net:
 * pushover-client:1.0.0}) gebauten Anfrage - siehe {@code ExecutionFinisher} im Client
 * (Alt-Code):
 *
 * <pre>
 * final PushoverRestClient client = new PushoverRestClient();
 * client.pushMessage(PushoverMessage.builderWithApiToken(apiToken)
 *         .setUserId(pushoverUserKey).setMessage(shortMessage)
 *         .setPriority(MessagePriority.HIGH).setTitle(title)
 *         .setUrl("http://waschportal.hilaren.de").setTitleForURL("Waschportal").build());
 * </pre>
 *
 * <p>Aus dem Bytecode von {@code PushoverRestClient#pushMessage} (siehe
 * kb/05-migration-plan.md, AP5, für die vollständige Herleitung) ergibt sich exakt diese
 * Form-URL-encoded-Anfrage per POST an {@code https://api.pushover.net/1/messages.json}:
 * Felder {@code token}, {@code user}, {@code message} (immer gesetzt), dann {@code title},
 * {@code url}, {@code url_title} (nur falls nicht {@code null} - hier immer gesetzt), dann
 * {@code device}/{@code timestamp}/{@code sound} (im Alt-Aufruf nicht gesetzt, hier
 * weggelassen) und zuletzt {@code priority} (nur falls != {@code NORMAL} - der Alt-Aufruf
 * setzt {@code HIGH}, dessen {@code toString()} den int-Wert {@code "1"} liefert).
 *
 * <p>Ersetzt Apache HttpClient/die {@code pushover-client}-Bibliothek durch
 * {@code java.net.http} (siehe kb/05-migration-plan.md, Technologie-Entscheidungen: "HTTP im
 * Terminal: java.net.http" - hier analog fürs Backend). Abweichung: die Ziel-URL ist
 * konfigurierbar ({@link NotificationsProperties.Pushover#getBaseUrl()}), damit Tests einen
 * lokalen Mock-Server verwenden können; der Produktionsdefault ist identisch zur
 * Alt-Code-Konstante.
 */
@Component
public class PushoverClient {

    /**
     * Entspricht dem im Alt-Aufruf fest verdrahteten {@code .setUrl(...)} - siehe Klassen-
     * Javadoc. Nicht konfigurierbar, exakt wie im Alt-Code.
     */
    static final String FIXED_URL = "http://waschportal.hilaren.de";

    /** Entspricht dem im Alt-Aufruf fest verdrahteten {@code .setTitleForURL(...)}. */
    static final String FIXED_URL_TITLE = "Waschportal";

    /** {@code MessagePriority.HIGH.toString()} im Alt-Code (siehe Klassen-Javadoc). */
    static final String PRIORITY_HIGH = "1";

    private final NotificationsProperties properties;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public PushoverClient(NotificationsProperties properties) {
        this.properties = properties;
    }

    /**
     * Sendet eine Pushover-Nachricht. Wirft bei Transport- oder Protokollfehlern eine
     * Exception - der Aufrufer ({@link NotificationService}) fängt sie ab, damit ein
     * Versandfehler keinen fachlichen Ablauf unterbricht (siehe dortige Begründung, 1:1
     * zum Alt-Code-Verhalten in {@code ExecutionFinisher}).
     */
    public Result sendMessage(String userKey, String title, String message) throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("token", this.properties.getPushover().getApiToken());
        form.put("user", userKey);
        form.put("message", message);
        form.put("title", title);
        form.put("url", FIXED_URL);
        form.put("url_title", FIXED_URL_TITLE);
        // device/timestamp/sound: im Alt-Aufruf nicht gesetzt (null) -> ausgelassen.
        form.put("priority", PRIORITY_HIGH);

        String body = encodeForm(form);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.properties.getPushover().getBaseUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), parsePushoverStatus(response.body()));
    }

    private static String encodeForm(Map<String, String> form) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private int parsePushoverStatus(String responseBody) {
        try {
            JsonNode node = this.objectMapper.readTree(responseBody);
            return node.path("status").asInt(-1);
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * @param httpStatus     HTTP-Statuscode der Antwort.
     * @param pushoverStatus Pushover-eigenes {@code status}-Feld aus dem JSON-Antwortkörper
     *                       ({@code 1} = Erfolg laut Pushover-API-Dokumentation), {@code -1}
     *                       falls nicht auswertbar.
     */
    public record Result(int httpStatus, int pushoverStatus) {
    }
}
