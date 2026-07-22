package org.kabieror.elwasys.raspiclient.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.kabieror.elwasys.raspiclient.api.dto.CardLoginRequest;
import org.kabieror.elwasys.raspiclient.api.dto.CreditResponse;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceDto;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceOverviewDto;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionDto;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionEndRequest;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionStartRequest;
import org.kabieror.elwasys.raspiclient.api.dto.LocationDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotDto;
import org.kabieror.elwasys.raspiclient.api.dto.UpdateDeconzUuidRequest;
import org.kabieror.elwasys.raspiclient.api.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Schlanke REST-Client-Schicht für die Backend-API v1 (Phase 4 AP4, siehe
 * docs/kb/05-migration-plan.md "Client-Cutover"). Ersetzt {@code Common.DataManager} als
 * Datenzugriffspfad des Terminals - mit der einen dokumentierten Ausnahme der
 * Fernwartungs-Registrierung ({@code LocationManager}/{@code MaintenanceServerManager}),
 * die bis AP5 auf dem Alt-TCP-Protokoll direkt gegen die Datenbank bleibt.
 * <p>
 * Baut auf {@code java.net.http} auf (Projektstandard seit Phase 4 AP2, siehe
 * {@code devices/deconz/DeconzApiAdapter}) statt einer weiteren HTTP-Client-Abhängigkeit.
 * Authentifizierung über den Standort-Token als {@code Authorization: Bearer <token>}
 * (siehe {@code backend.auth.terminal.TerminalTokenAuthenticationFilter}).
 * <p>
 * Fehlerbehandlung: jede Methode wirft {@link ApiException} bei einem Kommunikationsfehler
 * (Verbindung/Timeout, {@code httpStatus=0}) oder einer nicht-2xx-Antwort (der Backend-Fehler
 * wird dabei aus der RFC-7807-{@code ProblemDetail}-Antwort entnommen, siehe
 * {@link ApiException}).
 */
public class ApiClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HttpClient httpClient;
    private final URI baseUrl;
    private final String token;
    private final Gson gson;

    public ApiClient(String baseUrl, String token) {
        this(baseUrl, token, HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
    }

    /**
     * Konstruktor mit injizierbarem {@link HttpClient} (Tests können hier z. B. eine kürzere
     * Timeout-Konfiguration setzen).
     */
    public ApiClient(String baseUrl, String token, HttpClient httpClient) {
        String normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.baseUrl = URI.create(normalized);
        this.token = token;
        this.httpClient = httpClient;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class,
                        (com.google.gson.JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                                src == null ? null
                                        : new com.google.gson.JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(src)))
                .registerTypeAdapter(LocalDateTime.class,
                        (com.google.gson.JsonDeserializer<LocalDateTime>) (json, typeOfT, context) ->
                                json.isJsonNull() ? null
                                        : LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .create();
    }

    // --- Kartenlogin / Standort ------------------------------------------------------------

    public UserDto cardLogin(String cardId) throws ApiException {
        return post("api/v1/card-login", new CardLoginRequest(cardId), UserDto.class, null);
    }

    public LocationDto getMyLocation() throws ApiException {
        return get("api/v1/locations/me", LocationDto.class);
    }

    /**
     * Standort-Snapshot für die Offline-Buchungs-Vorbereitung (Phase 4 AP3/AP6, siehe
     * docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"). Wird
     * periodisch aufgerufen und von {@code offline.OfflineSnapshotStore} persistiert.
     */
    public SnapshotDto getSnapshot() throws ApiException {
        return get("api/v1/snapshot", SnapshotDto.class);
    }

    // --- Geräte ------------------------------------------------------------------------------

    public List<DeviceOverviewDto> getDevicesOverview() throws ApiException {
        return get("api/v1/devices/overview", DEVICE_OVERVIEW_LIST_TYPE);
    }

    public List<DeviceDto> getDevices(int userId) throws ApiException {
        return get("api/v1/devices?userId=" + userId, DEVICE_LIST_TYPE);
    }

    public DeviceOverviewDto updateDeconzUuid(int deviceId, String deconzUuid) throws ApiException {
        return post("api/v1/devices/" + deviceId + "/deconz-uuid", new UpdateDeconzUuidRequest(deconzUuid),
                DeviceOverviewDto.class, null);
    }

    // --- Ausführungen ------------------------------------------------------------------------

    public ExecutionDto createExecution(int userId, int deviceId, int programId, LocalDateTime clientTimestamp)
            throws ApiException {
        return createExecution(userId, deviceId, programId, clientTimestamp, UUID.randomUUID().toString());
    }

    /**
     * Wie {@link #createExecution(int, int, int, LocalDateTime)}, aber mit einem vom
     * Aufrufer vorgegebenen Idempotenz-Schlüssel statt einer frisch erzeugten UUID (Phase 4
     * AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"
     * Punkt 4 "Nachmeldung (Replay)"): der Aufrufer braucht den Schlüssel VOR dem Aufruf
     * (z. B. um ihn im Ereignis-Journal zu hinterlegen, falls die Anfrage wegen eines
     * Kommunikationsfehlers fehlschlägt), damit ein späterer Replay mit demselben Schlüssel
     * korrekt dedupliziert wird - auch dann, wenn die Anfrage den Server in Wahrheit bereits
     * erreicht hatte und nur die Antwort verloren ging.
     */
    public ExecutionDto createExecution(int userId, int deviceId, int programId, LocalDateTime clientTimestamp,
            String idempotencyKey) throws ApiException {
        return post("api/v1/executions",
                new ExecutionStartRequest(userId, deviceId, programId, clientTimestamp, Boolean.FALSE),
                ExecutionDto.class, idempotencyKey);
    }

    /**
     * Nachmeldung ({@code replay}) einer bereits offline gebuchten Ausführung (Issue #16,
     * privilegierter Nachbuchungs-Pfad): wie {@link #createExecution(int, int, int,
     * LocalDateTime, String)}, aber mit gesetztem {@code replay}-Flag, sodass das Backend die
     * fachlichen Wächter (Sperrung/Standort/Nutzbarkeit/Belegung/Guthaben) überspringt. Ein
     * nachgemeldetes Ereignis ist ein Fakt, keine Anfrage - ein fachlich abgelehnter Eintrag
     * würde sonst das gesamte Journal-Replay dauerhaft verklemmen.
     */
    public ExecutionDto replayCreateExecution(int userId, int deviceId, int programId, LocalDateTime clientTimestamp,
            String idempotencyKey) throws ApiException {
        return post("api/v1/executions",
                new ExecutionStartRequest(userId, deviceId, programId, clientTimestamp, Boolean.TRUE),
                ExecutionDto.class, idempotencyKey);
    }

    public ExecutionDto getExecution(int id) throws ApiException {
        return get("api/v1/executions/" + id, ExecutionDto.class);
    }

    public ExecutionDto finishExecution(int id, LocalDateTime clientTimestamp) throws ApiException {
        return finishExecution(id, clientTimestamp, UUID.randomUUID().toString());
    }

    /**
     * Wie {@link #finishExecution(int, LocalDateTime)}, mit vorgegebenem Idempotenz-Schlüssel
     * - siehe {@link #createExecution(int, int, int, LocalDateTime, String)} für die
     * Begründung.
     */
    public ExecutionDto finishExecution(int id, LocalDateTime clientTimestamp, String idempotencyKey)
            throws ApiException {
        return post("api/v1/executions/" + id + "/finish", new ExecutionEndRequest(clientTimestamp),
                ExecutionDto.class, idempotencyKey);
    }

    public ExecutionDto abortExecution(int id, LocalDateTime clientTimestamp) throws ApiException {
        return abortExecution(id, clientTimestamp, UUID.randomUUID().toString());
    }

    /**
     * Wie {@link #abortExecution(int, LocalDateTime)}, mit vorgegebenem Idempotenz-Schlüssel
     * - siehe {@link #createExecution(int, int, int, LocalDateTime, String)} für die
     * Begründung.
     */
    public ExecutionDto abortExecution(int id, LocalDateTime clientTimestamp, String idempotencyKey)
            throws ApiException {
        return post("api/v1/executions/" + id + "/abort", new ExecutionEndRequest(clientTimestamp),
                ExecutionDto.class, idempotencyKey);
    }

    public ExecutionDto resetExecution(int id) throws ApiException {
        String idempotencyKey = UUID.randomUUID().toString();
        return post("api/v1/executions/" + id + "/reset", null, ExecutionDto.class, idempotencyKey);
    }

    public CreditResponse getCredit(int userId) throws ApiException {
        return get("api/v1/users/" + userId + "/credit", CreditResponse.class);
    }

    // --- HTTP-Hilfsmethoden ------------------------------------------------------------------

    private static final Type DEVICE_OVERVIEW_LIST_TYPE =
            com.google.gson.reflect.TypeToken.getParameterized(List.class, DeviceOverviewDto.class).getType();
    private static final Type DEVICE_LIST_TYPE =
            com.google.gson.reflect.TypeToken.getParameterized(List.class, DeviceDto.class).getType();

    private <T> T get(String path, Type responseType) throws ApiException {
        HttpRequest request = requestBuilder(path).GET().build();
        HttpResponse<String> response = send(request);
        return parse(response, responseType);
    }

    private <T> T get(String path, Class<T> responseType) throws ApiException {
        return get(path, (Type) responseType);
    }

    private <T> T post(String path, Object body, Class<T> responseType, String idempotencyKey) throws ApiException {
        String json = body == null ? "" : this.gson.toJson(body);
        HttpRequest.Builder builder = requestBuilder(path).POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json");
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        HttpResponse<String> response = send(builder.build());
        return parse(response, responseType);
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(this.baseUrl.resolve(path)).timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + this.token)
                .header("Accept", "application/json");
    }

    /**
     * Anzahl zusätzlicher Versuche bei einem transienten I/O-Fehler (siehe
     * {@link #isTransientCommunicationFailure(IOException)}) - bewusst nur einer: ein
     * einmaliges, sofortiges Retry genügt, um die beobachtete CI-Flakiness abzufangen (siehe
     * docs/kb/05-migration-plan.md, Änderungslog "Phase 4 CI-Stabilität"), ohne den Normalpfad bei
     * einem echt nicht erreichbaren Backend spürbar zu verlangsamen.
     */
    private static final int TRANSIENT_RETRY_COUNT = 1;

    private HttpResponse<String> send(HttpRequest request) throws ApiException {
        IOException lastError = null;
        for (int attempt = 0; attempt <= TRANSIENT_RETRY_COUNT; attempt++) {
            try {
                return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                lastError = e;
                if (attempt < TRANSIENT_RETRY_COUNT && isTransientCommunicationFailure(e)) {
                    // Beobachtet in CI (siehe docs/kb/05-migration-plan.md, Änderungslog "Phase 4
                    // CI-Stabilität"): unter Last kann eine vom java.net.http-Verbindungspool
                    // wiederverwendete Keep-Alive-Verbindung serverseitig bereits geschlossen
                    // worden sein, bevor der Client sie erneut nutzt - der Schreibvorgang
                    // gelingt noch, die Antwort kommt aber als leeres EOF zurück ("HTTP/1.1
                    // header parser received no bytes"). Ein GET (idempotent) oder ein
                    // mutierender Aufruf mit Idempotency-Key (siehe Klassen-Kommentar) darf
                    // deshalb gefahrlos sofort wiederholt werden - kein Backoff, damit der
                    // Normalpfad nicht spürbar langsamer wird.
                    this.logger.warn("Transient I/O error talking to the backend, retrying once: {} {}",
                            request.method(), request.uri(), e);
                    continue;
                }
                this.logger.warn("Communication with the backend failed: {} {}", request.method(), request.uri(), e);
                throw new ApiException("Das Backend ist nicht erreichbar: " + e.getLocalizedMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException("Die Anfrage an das Backend wurde unterbrochen.", e);
            }
        }
        // Unreachable (the loop above always either returns or throws), but required so the
        // compiler sees every path returning/throwing.
        throw new ApiException("Das Backend ist nicht erreichbar: " + lastError.getLocalizedMessage(), lastError);
    }

    /**
     * Erkennt die transiente Fehlerklasse "Verbindung wurde angenommen, aber ohne Antwort
     * wieder geschlossen" (EOF/„received no bytes"/Connection reset) - im Unterschied zu
     * einem echten Kommunikationsfehler (Verbindung verweigert, DNS-Fehler, Timeout), der
     * weiterhin sofort als {@link ApiException} gemeldet wird (kein Retry, s.
     * {@link #TRANSIENT_RETRY_COUNT}).
     */
    private static boolean isTransientCommunicationFailure(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.io.EOFException) {
                return true;
            }
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("received no bytes") || lower.contains("eof reached")
                        || lower.contains("connection reset")) {
                    return true;
                }
            }
        }
        return false;
    }

    private <T> T parse(HttpResponse<String> response, Type responseType) throws ApiException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw toApiException(response);
        }
        if (responseType == null || response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return this.gson.fromJson(response.body(), responseType);
        } catch (JsonSyntaxException e) {
            throw new ApiException("Antwort des Backends konnte nicht gelesen werden.", e);
        }
    }

    /**
     * Übersetzt eine RFC-7807-{@code ProblemDetail}-Fehlerantwort (siehe
     * {@code backend.api.ApiExceptionHandler}) in eine {@link ApiException} mit
     * ausgewertetem {@code typeSlug}.
     */
    private ApiException toApiException(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();
        try {
            ProblemDetailDto problem = this.gson.fromJson(body, ProblemDetailDto.class);
            if (problem != null) {
                String slug = extractSlug(problem.type());
                return new ApiException(status, slug, problem.title(), problem.detail());
            }
        } catch (JsonSyntaxException e) {
            this.logger.debug("Could not parse error response body as ProblemDetail.", e);
        }
        return new ApiException(status, null, null, "HTTP " + status);
    }

    private static String extractSlug(String type) {
        if (type == null) {
            return null;
        }
        int idx = type.lastIndexOf(':');
        return idx >= 0 ? type.substring(idx + 1) : type;
    }

    private record ProblemDetailDto(String type, String title, int status, String detail) {
    }
}
