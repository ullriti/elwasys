package org.kabieror.elwasys.raspiclient.ws;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.kabieror.elwasys.common.Utilities;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.application.ICloseListener;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ausgehende WebSocket-Verbindung des Terminals zum Backend (Phase 4 AP5, siehe
 * docs/kb/05-migration-plan.md "Arbeitspakete Phase 4", AP5, und docs/kb/03-modules.md für das
 * vollständige Nachrichtenprotokoll). Ersetzt {@code MaintenanceServerManager} (Terminal
 * lauschte als TCP-Server, Portal wählte über eine in {@code locations} registrierte IP an) -
 * die Richtung dreht sich um: das Terminal baut die Verbindung selbst auf und hält sie, genau
 * wie es das seit Phase 4 AP4 für die REST-API bereits über {@code api/ApiClient} tut
 * (dieselbe {@code backend.url}/{@code backend.token}-Konfiguration, kein neuer Konfig-Schlüssel
 * nötig).
 * <p>
 * <b>Technologie</b>: {@code org.springframework.web.socket.client.standard.StandardWebSocketClient}
 * (JSR-356/Tyrus über {@code spring-boot-starter-websocket}, im Client bereits Dependency und
 * bereits für die deCONZ-Anbindung im selben Muster verwendet, siehe
 * {@code devices/deconz/DeconzEventListener}) statt eines weiteren WebSocket-Clients.
 * <p>
 * <b>Authentifizierung</b>: derselbe Standort-Token wie {@link org.kabieror.elwasys.raspiclient.api.ApiClient}
 * als {@code Authorization: Bearer <token>}-Header beim Handshake (siehe
 * {@code backend.ws.TerminalHandshakeInterceptor}/{@code TerminalTokenAuthenticationFilter}).
 * <p>
 * <b>Heartbeat</b>: das Backend sendet periodisch {@code PING} und schließt die Verbindung, wenn
 * es lange kein {@code PONG} sieht ({@code backend.ws.TerminalHeartbeatScheduler}) - dieser
 * Client muss dafür nur auf {@code PING} mit {@code PONG} antworten, siehe
 * {@link #handleTextMessage}. Ein eigener, vom Terminal ausgehender Heartbeat ist nicht
 * nötig (das Backend erkennt eine tote Verbindung ohnehin über sein eigenes Timeout).
 * <p>
 * <b>Reconnect</b>: bei Verbindungsfehler/-abbruch (Netzwerkausfall, Backend-Neustart, vom
 * Heartbeat erzwungener Verbindungsabbruch) wird automatisch mit exponentiell wachsender
 * Wartezeit (5s bis maximal 5min) erneut verbunden - identisches Muster zu
 * {@code DeconzEventListener#scheduleReconnect}.
 * <p>
 * <b>Fachfunktionen</b> (bedient dieselben Anfragen, die früher über das Alt-TCP-Protokoll
 * liefen, siehe {@code Common.maintenance.*}/das ehemalige {@code MaintenanceServerManager}):
 * <ul>
 *     <li>{@code STATUS_REQUEST} (portal-initiiert, siehe
 *         {@code backend.ws.TerminalMaintenanceService#requestStatus}): Client-Version,
 *         Startzeit, Ids der aktuell laufenden Ausführungen (rein lokal aus
 *         {@link ElwaManager#getExecutionManager()}, kein Netzwerkzugriff nötig) - fachlicher
 *         Nachfolger von {@code GetStatusRequest}/{@code GetStatusResponse}.</li>
 *     <li>{@code LOG_REQUEST}: aktueller Inhalt der Logdatei - fachlicher Nachfolger von
 *         {@code GetLogRequest}/{@code GetLogResponse} (identische Quelle,
 *         {@code Utilities#getCurrentLogFile()}).</li>
 *     <li>{@code RESTART_REQUEST}: Neustart der Anwendung
 *         ({@link ElwaManager#restart()}) - anders als das Alt-Protokoll (dort
 *         "fire-and-forget") bestätigt dieser Client den Empfang zuerst mit
 *         {@code RESTART_RESPONSE}, bevor der Neustart ausgeführt wird (siehe
 *         {@code TerminalWsMessageType} im Backend für die Begründung).</li>
 * </ul>
 * <p>
 * Die Verbindung überlebt einen vom Portal ausgelösten Neustart bewusst ({@link #onClose}
 * reagiert nur auf ein endgültiges Schließen der Anwendung, nicht auf {@code restart=true}) -
 * identisch zum Verhalten des ehemaligen {@code MaintenanceServerManager}.
 */
public class TerminalWebSocketClient extends TextWebSocketHandler implements ICloseListener {

    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 300;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ElwaManager manager;
    private final URI wsUri;
    private final String token;
    private final String clientUid;
    private final Gson gson = new GsonBuilder().create();

    private final WebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TerminalWebSocketClient-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean isReconnectRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile WebSocketSession session;
    private int reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;

    public TerminalWebSocketClient(ElwaManager manager, String backendUrl, String token, String clientUid) {
        this.manager = manager;
        this.wsUri = toWsUri(backendUrl);
        this.token = token;
        this.clientUid = clientUid;
    }

    private static URI toWsUri(String backendUrl) {
        String normalized = backendUrl.endsWith("/") ? backendUrl : backendUrl + "/";
        // http:// -> ws://, https:// -> wss:// (replaceFirst only touches the "http" prefix,
        // leaving a trailing "s" - if present - untouched).
        String wsBase = normalized.replaceFirst("^http", "ws");
        return URI.create(wsBase + "api/v1/terminal-ws");
    }

    /**
     * Baut die Verbindung auf (asynchron) und aktiviert den automatischen Reconnect.
     */
    public void start() {
        this.stopped.set(false);
        openConnection();
    }

    /**
     * Schließt die Verbindung endgültig und deaktiviert den Reconnect.
     */
    public void stop() {
        this.stopped.set(true);
        this.reconnectScheduler.shutdownNow();
        WebSocketSession s = this.session;
        if (s != null && s.isOpen()) {
            try {
                s.close();
            } catch (final Exception e) {
                this.logger.debug("Failed to close the backend WebSocket connection.", e);
            }
        }
    }

    @Override
    public void onClose(boolean restart) {
        // Ein Neustart (siehe ElwaManager#restart) soll die Verbindung NICHT abbauen -
        // identisches Verhalten zum ehemaligen MaintenanceServerManager.
        if (!restart) {
            stop();
        }
    }

    private void openConnection() {
        if (this.stopped.get()) {
            return;
        }
        this.logger.info("Connecting to the backend WebSocket at {}.", this.wsUri);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + this.token);
        this.client.execute(this, headers, this.wsUri).whenComplete((result, ex) -> {
            this.isReconnectRunning.set(false);
            if (ex != null) {
                this.logger.warn("Could not connect to the backend WebSocket: {}", ex.toString());
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (this.stopped.get() || this.reconnectScheduler.isShutdown()) {
            return;
        }
        if (this.isReconnectRunning.compareAndSet(false, true)) {
            this.logger.info("Scheduling a reconnect to the backend WebSocket in {}s.", this.reconnectDelaySeconds);
            this.reconnectScheduler.schedule(this::openConnection, this.reconnectDelaySeconds, TimeUnit.SECONDS);
            this.reconnectDelaySeconds =
                    (int) Math.min(MAX_RECONNECT_DELAY_SECONDS, Math.round(this.reconnectDelaySeconds * 1.5));
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.logger.info("Connected to the backend WebSocket.");
        this.session = session;
        this.reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;
        sendHello(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.logger.warn("Backend WebSocket connection closed: {}", status);
        if (session.equals(this.session)) {
            this.session = null;
        }
        if (!this.stopped.get()) {
            scheduleReconnect();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        this.logger.warn("Transport error on the backend WebSocket connection.", exception);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        TerminalWsMessage incoming;
        try {
            incoming = this.gson.fromJson(message.getPayload(), TerminalWsMessage.class);
        } catch (final JsonSyntaxException e) {
            this.logger.warn("Received a malformed message on the backend WebSocket connection.", e);
            return;
        }
        if (incoming == null || incoming.getType() == null) {
            return;
        }
        switch (incoming.getType()) {
            case HELLO_ACK -> this.logger.debug("Backend acknowledged HELLO: {}", incoming.getPayload());
            case PING -> send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.PONG, Map.of()));
            case PONG -> this.logger.debug("Received an unsolicited PONG from the backend.");
            case STATUS_REQUEST ->
                    send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.STATUS_RESPONSE,
                            buildStatusPayload()));
            case LOG_REQUEST ->
                    send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.LOG_RESPONSE,
                            buildLogPayload()));
            case RESTART_REQUEST -> handleRestartRequest(session, incoming);
            case ERROR -> this.logger.warn("Backend reported a protocol error: {}", incoming.getPayload());
            default ->
                    this.logger.debug("Ignoring unhandled message type {} from the backend.", incoming.getType());
        }
    }

    private void sendHello(WebSocketSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientVersion", Utilities.APP_VERSION);
        payload.put("clientUid", this.clientUid);
        send(session, TerminalWsMessage.of(TerminalWsMessageType.HELLO, payload));
    }

    private Map<String, Object> buildStatusPayload() {
        List<Integer> runningExecutionIds = new ArrayList<>();
        if (this.manager.getExecutionManager() != null) {
            for (ClientExecution execution : this.manager.getExecutionManager().getRunningExecutions()) {
                runningExecutionIds.add(execution.getId());
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("clientVersion", Utilities.APP_VERSION);
        payload.put("startupTime", this.manager.getStartupTime().toString());
        payload.put("runningExecutionIds", runningExecutionIds);
        return payload;
    }

    private Map<String, Object> buildLogPayload() {
        List<String> lines;
        try {
            String logFileName = Utilities.getCurrentLogFile();
            lines = logFileName == null ? new ArrayList<>() : Files.readAllLines(new File(logFileName).toPath());
        } catch (final Exception e) {
            this.logger.error("Could not read the log file.", e);
            lines = new ArrayList<>();
            lines.add("Could not read the log file.");
            lines.add(String.valueOf(e));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("lines", lines);
        return payload;
    }

    private void handleRestartRequest(WebSocketSession session, TerminalWsMessage incoming) {
        this.logger.info("Backend requested a restart of the application.");
        send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.RESTART_RESPONSE,
                Map.of("accepted", true)));
        this.manager.restart();
    }

    private void send(WebSocketSession session, TerminalWsMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(this.gson.toJson(message)));
            }
        } catch (final Exception e) {
            this.logger.warn("Failed to send a message on the backend WebSocket connection.", e);
        }
    }
}
