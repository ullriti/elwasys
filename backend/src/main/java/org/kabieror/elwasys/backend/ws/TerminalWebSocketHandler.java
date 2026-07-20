package org.kabieror.elwasys.backend.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Terminal-WebSocket-Endpunkt ({@code /api/v1/terminal-ws}, AP4, siehe
 * kb/05-migration-plan.md/kb/03-modules.md für das vollständige Protokoll). Kanal-Fundament
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

    private final TerminalConnectionRegistry connectionRegistry;

    private final ObjectMapper objectMapper;

    public TerminalWebSocketHandler(TerminalConnectionRegistry connectionRegistry, ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Integer locationId = locationId(session);
        LOG.info("Terminal WebSocket connected: location {} ({}), session {}.", locationId, locationName(session),
                session.getId());
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
            case STATUS_RESPONSE ->
                    LOG.debug("Received STATUS_RESPONSE from location {} (session {}) - no handler wired up yet"
                            + " (Fernwartungs-Portierung folgt in Phase 3/4).", locationId(session), session.getId());
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
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(this.objectMapper.writeValueAsString(message)));
        }
    }

    private Integer locationId(WebSocketSession session) {
        return (Integer) session.getAttributes().get(TerminalHandshakeInterceptor.ATTR_LOCATION_ID);
    }

    private String locationName(WebSocketSession session) {
        return (String) session.getAttributes().get(TerminalHandshakeInterceptor.ATTR_LOCATION_NAME);
    }
}
