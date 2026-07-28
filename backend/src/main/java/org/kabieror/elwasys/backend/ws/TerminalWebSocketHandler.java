package org.kabieror.elwasys.backend.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Terminal-WebSocket-Endpunkt ({@code /api/v1/terminal-ws}, AP4, siehe
 * docs/kb/05-migration-plan.md/kb/03-modules.md für das vollständige Protokoll). Kanal-Fundament
 * für künftigen Ereignis-Push Backend-&gt;Terminal und die Fernwartung (Status/Logs/Restart,
 * fachliche Referenz {@code Common.maintenance.*}) - dieses Arbeitspaket implementiert das
 * Nachrichtenformat, die Verbindungsregistry, Heartbeat sowie HELLO/HELLO_ACK und
 * STATUS_REQUEST/STATUS_RESPONSE als Gerüst; die vollständige Fernwartungs-Portierung folgt
 * in Phase 3/4.
 *
 * <p>Der Standort-Kontext (aus dem beim Handshake geprüften Standort-Token, siehe
 * {@link TerminalHandshakeInterceptor}) steht in den Session-Attributen und wird für JEDE
 * Nachricht dieser Verbindung wiederverwendet - eine einzelne WebSocket-Verbindung gehört
 * genau einem Standort, es gibt keinen Wechsel innerhalb einer Session.
 */
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    /** Frame-Grenze je Terminal-Verbindung, siehe {@link #afterConnectionEstablished} (ADR 0024). */
    static final int MAX_TEXT_MESSAGE_BUFFER_BYTES = 1024 * 1024;

    private final TerminalConnectionRegistry connectionRegistry;

    private final ObjectMapper objectMapper;

    private final TerminalMaintenanceService maintenanceService;

    private final TerminalOfflineIncidentService incidentService;

    public TerminalWebSocketHandler(TerminalConnectionRegistry connectionRegistry, ObjectMapper objectMapper,
            TerminalMaintenanceService maintenanceService, TerminalOfflineIncidentService incidentService) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
        this.maintenanceService = maintenanceService;
        this.incidentService = incidentService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Integer locationId = locationId(session);
        LOG.info("Terminal WebSocket connected: location {} ({}), session {}.", locationId, locationName(session),
                session.getId());
        // Frame-Grenze DIESER Verbindung anheben (ADR 0024). Der JSR-356-Default von 8 KiB ist
        // für dieses Protokoll zu knapp: eine LOG_RESPONSE überschritt ihn schon nach wenigen
        // Minuten Terminal-Betrieb, woraufhin Tomcat die Verbindung mit 1009 schloss und damit
        // auch Status/Neustart/Vorfallsmeldungen bis zum Reconnect mit abriss. Das Terminal
        // deckelt seine Log-Antwort zusätzlich an der Quelle (TerminalWebSocketClient) - dieser
        // Wert ist das Sicherheitsnetz und muss zu dem der Gegenstelle passen, weil jede Seite
        // nur ihren EIGENEN Empfangspuffer prüft.
        //
        // Bewusst je Session statt über ein ServletServerContainerFactoryBean: das gälte
        // container-weit und verlangt einen ECHTEN Servlet-Container - in Tests mit
        // @SpringBootTest(webEnvironment=MOCK) gibt es keinen, dort scheiterte die
        // Kontext-Initialisierung.
        session.setTextMessageSizeLimit(MAX_TEXT_MESSAGE_BUFFER_BYTES);
        this.connectionRegistry.register(locationId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        TerminalWsMessage incoming;
        try {
            incoming = this.objectMapper.readValue(message.getPayload(), TerminalWsMessage.class);
        } catch (JsonProcessingException e) {
            LOG.debug("Received malformed message on terminal WebSocket session {}.", session.getId(), e);
            send(session, TerminalWsMessage.of(TerminalWsMessageType.ERROR,
                    Map.of("reason", "malformed-message", "detail", "Could not parse the message as JSON.")));
            return;
        }

        switch (incoming.type()) {
            case HELLO -> handleHello(session, incoming);
            case PING -> send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.PONG, Map.of()));
            case PONG -> this.connectionRegistry.markPong(locationId(session), session);
            case STATUS_REQUEST -> handleStatusRequest(session, incoming);
            // Phase 3 AP4 (LOG_RESPONSE/RESTART_RESPONSE) + Phase 4 AP5 (STATUS_RESPONSE,
            // additiv ergänzt): Antworten des Terminals auf eine portal-initiierte
            // LOG_REQUEST/RESTART_REQUEST/STATUS_REQUEST (siehe TerminalMaintenanceService) -
            // route sie an die wartende Anfrage zurück, statt sie (wie alles andere) mit
            // "not-implemented" zu beantworten. Berührt NICHT den Gerüst-Pfad oben
            // (handleStatusRequest), der auf ein vom TERMINAL selbst gesendetes
            // STATUS_REQUEST antwortet - beide Nachrichtentypen laufen in dieselbe Richtung
            // (Terminal -> Backend) nie gleichzeitig für dieselbe Anfrage auf.
            case LOG_RESPONSE, RESTART_RESPONSE, STATUS_RESPONSE ->
                    this.maintenanceService.completeIfPending(locationId(session), incoming);
            case OFFLINE_INCIDENT -> handleOfflineIncident(session, incoming);
            default -> send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.ERROR,
                    Map.of("reason", "not-implemented",
                            "detail", "Message type " + incoming.type() + " is not implemented in Phase 2 (AP4).")));
        }
    }

    private void handleHello(WebSocketSession session, TerminalWsMessage incoming) throws Exception {
        Map<String, Object> ack = Map.of("locationId", locationId(session), "locationName", locationName(session),
                "serverTime", Instant.now().toString(), "protocolVersion", TerminalWsMessage.PROTOCOL_VERSION);
        send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.HELLO_ACK, ack));
    }

    private void handleStatusRequest(WebSocketSession session, TerminalWsMessage incoming) throws Exception {
        // Gerüst (AP4): die volle Fernwartungs-Portierung (echter Terminal-Status: laufende
        // Ausführungen, Backlight, Interface-Status - fachliche Referenz GetStatusResponse in
        // Common.maintenance) folgt in Phase 3/4. Hier wird nur bewiesen, dass das
        // Anfrage-/Antwort-Paar über die Verbindung funktioniert.
        Map<String, Object> status = Map.of("locationId", locationId(session), "locationName", locationName(session),
                "connectedSince", this.connectionRegistry.connectedSince(locationId(session))
                        .map(Instant::toString).orElse(null),
                "serverTime", Instant.now().toString());
        send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.STATUS_RESPONSE, status));
    }

    /**
     * Nimmt eine unaufgeforderte Vorfalls-Meldung des Terminals entgegen (Issue #89, siehe
     * {@link TerminalWsMessageType#OFFLINE_INCIDENT}) und bestätigt sie.
     *
     * <p>Der Standort kommt aus der Session (beim Handshake per Token geprüft), NICHT aus der
     * Payload - ein Terminal kann so keine Vorfälle für einen fremden Standort melden (dasselbe
     * Prinzip wie bei der Fernwartungs-Standortprüfung, Issue #26).
     *
     * <p>Eine fehlerhafte Meldung wird mit {@code ERROR} beantwortet statt die Verbindung zu
     * belasten; das Terminal behandelt beide Antworten gleich (es kann den Vorfall ohnehin nicht
     * "besser" melden) - die Zustellung ist bewusst best-effort, der lokale Log-Eintrag am
     * Terminal bleibt als Rückfallebene bestehen.
     */
    private void handleOfflineIncident(WebSocketSession session, TerminalWsMessage incoming) throws Exception {
        Map<String, Object> payload = incoming.payload() == null ? Map.of() : incoming.payload();
        String incidentKey = asString(payload.get("incidentKey"));
        String kind = asString(payload.get("kind"));
        String reason = asString(payload.get("reason"));
        if (incidentKey == null || kind == null) {
            send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.ERROR,
                    Map.of("reason", "invalid-incident", "detail", "incidentKey and kind are required.")));
            return;
        }
        try {
            this.incidentService.report(locationId(session), incidentKey, kind, asString(payload.get("entryType")),
                    asString(payload.get("idempotencyKey")), asInteger(payload.get("userId")),
                    asDecimal(payload.get("chargedPrice")), reason == null ? "(kein Grund gemeldet)" : reason,
                    asDateTime(payload.get("occurredAt")));
            send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.OFFLINE_INCIDENT_ACK,
                    Map.of("incidentKey", incidentKey)));
        } catch (RuntimeException e) {
            LOG.warn("Could not record an offline incident reported by location {}.", locationId(session), e);
            send(session, TerminalWsMessage.inReplyTo(incoming, TerminalWsMessageType.ERROR,
                    Map.of("reason", "incident-not-recorded", "detail", String.valueOf(e.getMessage()))));
        }
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = asString(value);
        try {
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal asDecimal(Object value) {
        String text = asString(value);
        try {
            return text == null ? null : new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Der Zeitstempel stammt von der Terminal-Uhr und ist rein informativ - ein unlesbarer Wert
     * darf die Meldung nicht verwerfen (der Vorfall selbst ist die Information).
     */
    private static LocalDateTime asDateTime(Object value) {
        String text = asString(value);
        try {
            return text == null ? null : LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Integer locationId = locationId(session);
        LOG.info("Terminal WebSocket disconnected: location {}, session {}, status {}.", locationId, session.getId(),
                status);
        this.connectionRegistry.unregister(locationId, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.warn("Transport error on terminal WebSocket session {}.", session.getId(), exception);
    }

    private void send(WebSocketSession session, TerminalWsMessage message) throws Exception {
        // Über die in der Registry hinterlegte, serialisierungsgeschützte Session senden (siehe
        // TerminalConnectionRegistry#register): auf dieselbe Verbindung schreiben auch der
        // Heartbeat-Scheduler und die portal-initiierte Fernwartung. Ohne diesen Umweg gingen
        // die Antworten hier an der Serialisierung vorbei und könnten mit jenen kollidieren
        // ("The remote endpoint was in state [TEXT_FULL_WRITING]"). Ist der Standort (noch)
        // nicht registriert - z.B. eine bereits ersetzte Session -, wird bewusst auf die eigene
        // Session zurückgefallen, damit eine Antwort eher rausgeht als gar nicht.
        WebSocketSession target = this.connectionRegistry.guardedSession(locationId(session), session)
                .orElse(session);
        if (target.isOpen()) {
            target.sendMessage(new TextMessage(this.objectMapper.writeValueAsString(message)));
        }
    }

    private Integer locationId(WebSocketSession session) {
        return (Integer) session.getAttributes().get(TerminalHandshakeInterceptor.ATTR_LOCATION_ID);
    }

    private String locationName(WebSocketSession session) {
        return (String) session.getAttributes().get(TerminalHandshakeInterceptor.ATTR_LOCATION_NAME);
    }
}
