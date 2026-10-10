package org.kabieror.elwasys.raspiclient.ws;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.kabieror.elwasys.common.Utilities;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.application.ICloseListener;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.offline.OfflineIncident;
import org.kabieror.elwasys.raspiclient.offline.OfflineIncidentOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
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
 * nötig - wohl aber ein <b>Leerlauf-Wächter</b> ({@link #checkLiveness}): das Backend erkennt
 * eine tote Verbindung über sein Timeout und schließt sie, aber dieses Schließen muss das
 * Terminal nicht erreichen. Am 2026-09-29 blieb auf beiden Standorten die TCP-Verbindung
 * {@code ESTABLISHED}, ohne dass je wieder ein Byte floss - {@code afterConnectionClosed} kam
 * nie, der Reconnect wurde nie geplant, und der Kanal blieb 11 Tage stumm (Backend:
 * {@code operational} = 503). Kommt länger als {@link #IDLE_TIMEOUT} keine Nachricht des
 * Backends (es pingt alle 30 s), gilt die Verbindung als tot und wird neu aufgebaut.
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
 *     <li>{@code LOG_REQUEST}: das ENDE der Logdatei - fachlicher Nachfolger von
 *         {@code GetLogRequest}/{@code GetLogResponse} (identische Quelle,
 *         {@code Utilities#getCurrentLogFile()}), gedeckelt auf {@link #LOG_MAX_LINES} Zeilen
 *         bzw. {@link #LOG_MAX_BYTES} (ADR 0024 - die ganze Datei sprengte die Frame-Grenze und
 *         riss die Verbindung ab).</li>
 *     <li>{@code RESTART_REQUEST}: Neustart der Anwendung
 *         ({@link ElwaManager#restart()}) - anders als das Alt-Protokoll (dort
 *         "fire-and-forget") bestätigt dieser Client den Empfang zuerst mit
 *         {@code RESTART_RESPONSE}, bevor der Neustart ausgeführt wird (siehe
 *         {@code TerminalWsMessageType} im Backend für die Begründung).</li>
 *     <li>{@code OFFLINE_INCIDENT} (terminal-initiiert, unaufgefordert, Issue #89): Meldung eines
 *         Dead-Letter-/Geister-Execution-Vorfalls aus der Offline-Robustheit. Zustellweg der
 *         persistenten {@link OfflineIncidentOutbox} - die Meldung wird beim Verbindungsaufbau
 *         nachgereicht und erst nach {@code OFFLINE_INCIDENT_ACK} aus der Outbox entfernt.</li>
 * </ul>
 * <p>
 * Die Verbindung überlebt einen vom Portal ausgelösten Neustart bewusst ({@link #onClose}
 * reagiert nur auf ein endgültiges Schließen der Anwendung, nicht auf {@code restart=true}) -
 * identisch zum Verhalten des ehemaligen {@code MaintenanceServerManager}.
 */
public class TerminalWebSocketClient extends TextWebSocketHandler implements ICloseListener {

    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 300;

    /**
     * Ab dieser Stille gilt die Verbindung als tot. Das Backend pingt alle 30 s und schließt
     * selbst nach 90 s ohne PONG ({@code TerminalHeartbeatScheduler}); 120 s lassen also drei
     * verpasste PINGs plus Puffer zu, bevor das Terminal eingreift - ein einzelner
     * Netz-Schluckauf löst keinen Neuaufbau aus.
     */
    static final Duration IDLE_TIMEOUT = Duration.ofSeconds(120);
    private static final long WATCHDOG_INTERVAL_SECONDS = 30;

    /**
     * Obergrenze für den Verbindungsaufbau. Ohne sie bliebe ein nie abschließender Handshake
     * (halboffenes TCP beim Connect) für immer "Reconnect läuft": {@link #scheduleReconnect}
     * täte nichts mehr, und es gäbe weder Session noch neuen Versuch - derselbe stumme
     * Stillstand wie beim Leerlauf, nur vor dem ersten Byte.
     */
    private static final long CONNECT_TIMEOUT_SECONDS = 60;

    /**
     * Obergrenzen der {@code LOG_RESPONSE}-Nutzlast (ADR 0024). Die Antwort geht als EIN
     * Text-Frame zum Backend; ohne Deckelung wächst sie mit der Logdatei, bis sie die
     * Frame-Grenze sprengt und die Verbindung mit {@code 1009} abreißt (vor diesem Fix: schon
     * nach wenigen Minuten Terminal-Betrieb, weil logback nur täglich rollt).
     */
    private static final int LOG_MAX_LINES = 1000;
    private static final long LOG_MAX_BYTES = 128L * 1024;

    /**
     * Frame-Grenze dieser Verbindung, bewusst weit über der gedeckelten {@code LOG_RESPONSE}
     * (siehe oben) - der JSR-356-Default von 8 KiB ist für dieses Protokoll zu knapp. Die
     * Gegenstelle setzt denselben Wert je Session ({@code backend.ws.TerminalWebSocketHandler});
     * beide Seiten müssen zueinander passen, weil jede Seite nur ihren EIGENEN Empfangspuffer
     * prüft.
     */
    private static final int MAX_TEXT_MESSAGE_BUFFER_BYTES = 1024 * 1024;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ElwaManager manager;
    private final URI wsUri;
    private final String token;
    private final String clientUid;
    private final OfflineIncidentOutbox incidentOutbox;
    private final Gson gson = new GsonBuilder().create();

    private final WebSocketClient client = createClient();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "TerminalWebSocketClient-reconnect");
        t.setDaemon(true);
        return t;
    });
    /**
     * Serialisiert alle Sendevorgänge: seit Issue #89 sendet nicht mehr nur der WebSocket-Thread
     * (Antworten auf Anfragen des Backends), sondern auch der Replay-Thread (Vorfallsmeldungen
     * aus der Outbox). Eine {@code WebSocketSession} verträgt keine parallelen Schreibvorgänge.
     */
    private final Object sendLock = new Object();
    private final AtomicBoolean isReconnectRunning = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile WebSocketSession session;
    /**
     * Serialisiert die Wechsel von {@link #session} (Aufbau, Schließen, Wächter). Ohne ihn
     * könnte der Wächter eine zwischenzeitlich frisch aufgebaute Session mit {@code null}
     * überschreiben und damit verwaisen.
     */
    private final Object sessionLock = new Object();
    private final AtomicBoolean watchdogScheduled = new AtomicBoolean(false);
    /**
     * Monotone Zeit ({@link System#nanoTime()}) der letzten Nachricht des Backends bzw. des
     * Verbindungsaufbaus. Bewusst NICHT die Wanduhr: ein Pi ohne Hardware-Uhr springt nach dem
     * Boot per NTP - vorwärts würde eine gesunde Verbindung abgeschossen, rückwärts wäre der
     * Wächter blind, bis die Uhr aufgeholt hat.
     */
    private volatile long lastInboundNanos;
    private volatile int reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;

    public TerminalWebSocketClient(ElwaManager manager, String backendUrl, String token, String clientUid,
            OfflineIncidentOutbox incidentOutbox) {
        this.manager = manager;
        this.wsUri = toWsUri(backendUrl);
        this.token = token;
        this.clientUid = clientUid;
        this.incidentOutbox = incidentOutbox;
    }

    /**
     * {@link StandardWebSocketClient} mit angehobener Frame-Grenze (siehe
     * {@link #MAX_TEXT_MESSAGE_BUFFER_BYTES}). Der parameterlose Konstruktor nähme den
     * JSR-356-Container mit dessen Default-Puffer (8 KiB) - zu klein für dieses Protokoll.
     */
    private static WebSocketClient createClient() {
        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_BYTES);
        return new StandardWebSocketClient(container);
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
        // Diese Verbindung ist der Zustellweg der Vorfalls-Outbox (Issue #89) - die Outbox selbst
        // kennt weder WebSocket noch Protokoll. Erst hier (nicht im Konstruktor) verdrahtet, damit
        // kein halb konstruiertes "this" veroeffentlicht wird.
        this.incidentOutbox.setSender(this::sendIncident);
        // Auf demselben Ein-Thread-Scheduler wie der Reconnect: beide arbeiten nur kurz und
        // blockieren nie. Der Aufruf faengt jede Ausnahme selbst ab - ein periodischer Task, der
        // eine Ausnahme wirft, wird von ScheduledExecutorService STILLSCHWEIGEND nie wieder
        // ausgefuehrt, und genau das waere hier der gleiche stumme Stillstand wie der, den der
        // Waechter beheben soll.
        if (this.watchdogScheduled.compareAndSet(false, true)) {
            this.reconnectScheduler.scheduleWithFixedDelay(this::runLivenessCheck, WATCHDOG_INTERVAL_SECONDS,
                    WATCHDOG_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
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
        this.client.execute(this, headers, this.wsUri).orTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> {
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

    private void runLivenessCheck() {
        try {
            checkLiveness(System.nanoTime());
        } catch (final Throwable e) {
            // Throwable, nicht Exception: auch ein Error (z.B. NoClassDefFoundError nach einem
            // Jar-Update) wuerde den periodischen Task sonst still beenden.
            this.logger.warn("Liveness check of the backend WebSocket connection failed.", e);
        }
    }

    /**
     * Leerlauf-Wächter: erklärt eine Verbindung für tot, von der länger als
     * {@link #IDLE_TIMEOUT} nichts mehr kam, und baut sie neu auf. Verlässt sich bewusst NICHT
     * auf {@link #afterConnectionClosed}, weil genau dieser Rückruf im beobachteten Ausfall nie
     * kam. Die alte Session wird nur best effort geschlossen (das Schließen einer halboffenen
     * Verbindung kann hängen) und gilt sofort als abgelöst.
     * <p>
     * Paketsichtbar und mit übergebener Uhrzeit, damit der Test ohne Warten auskommt.
     */
    void checkLiveness(long nowNanos) {
        if (this.stopped.get()) {
            return;
        }
        final WebSocketSession dead;
        final long idleSeconds;
        synchronized (this.sessionLock) {
            dead = this.session;
            idleSeconds = TimeUnit.NANOSECONDS.toSeconds(nowNanos - this.lastInboundNanos);
            if (dead == null || nowNanos - this.lastInboundNanos <= IDLE_TIMEOUT.toNanos()) {
                return;
            }
            this.session = null;
        }
        this.logger.warn("No message from the backend for {}s - treating the WebSocket connection as dead and "
                + "reconnecting.", idleSeconds);
        // Eigener Wegwerf-Thread: ein Close auf halboffener Verbindung darf weder den Wächter
        // noch den Reconnect aufhalten.
        final Thread closer = new Thread(() -> {
            try {
                dead.close(CloseStatus.GOING_AWAY.withReason("No message from the backend"));
            } catch (final Exception e) {
                this.logger.debug("Failed to close the dead backend WebSocket connection.", e);
            }
        }, "TerminalWebSocketClient-close");
        closer.setDaemon(true);
        closer.start();
        scheduleReconnect();
    }

    /** Für Tests: ob ein Reconnect geplant oder gerade im Aufbau ist. */
    boolean isReconnectPending() {
        return this.isReconnectRunning.get();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.logger.info("Connected to the backend WebSocket.");
        synchronized (this.sessionLock) {
            this.session = session;
            this.lastInboundNanos = System.nanoTime();
        }
        this.reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;
        sendHello(session);
        // Ein Offline-Vorfall entsteht typischerweise waehrend einer Stoerung, also genau dann,
        // wenn diese Verbindung weg ist - darum jetzt (wieder verbunden) alles Ausstehende
        // nachreichen (Issue #89). Doppelmeldungen sind dank incidentKey unschaedlich.
        this.incidentOutbox.flush();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        this.logger.warn("Backend WebSocket connection closed: {}", status);
        synchronized (this.sessionLock) {
            final WebSocketSession current = this.session;
            if (current != null && !session.equals(current)) {
                // Eine vom Leerlauf-Waechter abgeloeste Session meldet sich verspaetet: die
                // laufende neue Verbindung darf dadurch nicht noch einmal neu aufgebaut werden.
                this.logger.debug("Ignoring the close of a superseded backend WebSocket session.");
                return;
            }
            if (session.equals(current)) {
                this.session = null;
            }
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
        // Nur die AKTUELLE Session zaehlt: Nachzuegler einer abgeloesten Session duerfen die
        // Stille der laufenden nicht verdecken.
        if (session.equals(this.session)) {
            this.lastInboundNanos = System.nanoTime();
        }
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
            case OFFLINE_INCIDENT_ACK -> handleOfflineIncidentAck(incoming);
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
            lines = Utilities.readLogTail(Utilities.getCurrentLogFile(), LOG_MAX_LINES, LOG_MAX_BYTES);
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

    /**
     * Zustellweg der Vorfalls-Outbox (Issue #89): sendet eine Meldung als unaufgeforderte
     * {@code OFFLINE_INCIDENT}-Nachricht. Steht die Verbindung gerade nicht, wird {@code false}
     * gemeldet - die Meldung bleibt dann in der Outbox und wird beim naechsten
     * Verbindungsaufbau erneut versucht (siehe {@link #afterConnectionEstablished}).
     *
     * <p>Der Standort wird bewusst NICHT mitgeschickt: das Backend leitet ihn aus der per Token
     * authentifizierten Session ab, sodass ein Terminal keine Vorfälle für einen fremden Standort
     * melden kann.
     */
    private boolean sendIncident(OfflineIncident incident) {
        WebSocketSession currentSession = this.session;
        if (currentSession == null || !currentSession.isOpen()) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("incidentKey", incident.incidentKey());
        payload.put("kind", incident.kind());
        payload.put("entryType", incident.entryType());
        payload.put("idempotencyKey", incident.idempotencyKey());
        payload.put("userId", incident.userId());
        // Betrag und Zeitstempel als String: so kommen sie ohne Genauigkeitsverlust bzw. ohne
        // Zeitzonen-Interpretation beim Backend an (dort BigDecimal/LocalDateTime).
        payload.put("chargedPrice", incident.chargedPrice() == null ? null : incident.chargedPrice().toPlainString());
        payload.put("reason", incident.reason());
        payload.put("occurredAt", incident.occurredAt() == null ? null : incident.occurredAt().toString());
        try {
            synchronized (this.sendLock) {
                currentSession.sendMessage(
                        new TextMessage(this.gson.toJson(TerminalWsMessage.of(TerminalWsMessageType.OFFLINE_INCIDENT,
                                payload))));
            }
            return true;
        } catch (final Exception e) {
            this.logger.warn("Failed to report an offline incident on the backend WebSocket connection.", e);
            return false;
        }
    }

    /**
     * Nimmt die Quittung des Backends entgegen und entfernt die Meldung damit aus der Outbox -
     * erst jetzt gilt der Vorfall als übermittelt (Issue #89).
     */
    private void handleOfflineIncidentAck(TerminalWsMessage incoming) {
        Object incidentKey = incoming.getPayload() == null ? null : incoming.getPayload().get("incidentKey");
        if (incidentKey != null) {
            this.incidentOutbox.acknowledge(String.valueOf(incidentKey));
        }
    }

    private void send(WebSocketSession session, TerminalWsMessage message) {
        try {
            synchronized (this.sendLock) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(this.gson.toJson(message)));
                }
            }
        } catch (final Exception e) {
            this.logger.warn("Failed to send a message on the backend WebSocket connection.", e);
        }
    }
}
