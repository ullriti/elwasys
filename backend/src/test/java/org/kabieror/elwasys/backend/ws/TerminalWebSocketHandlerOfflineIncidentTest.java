package org.kabieror.elwasys.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Regressionstests für die Verarbeitung von {@code OFFLINE_INCIDENT} im
 * {@link TerminalWebSocketHandler} (Issue #89).
 *
 * <p>Deckt die beiden Zusicherungen ab, die zuvor nur als Kommentar im Code standen und im
 * Review-Gate als ungetestet beanstandet wurden:
 * <ol>
 *   <li><b>Sicherheit</b>: der Standort stammt AUSSCHLIESSLICH aus der beim Handshake
 *       token-geprüften Session - ein Terminal darf keine Vorfälle für einen fremden Standort
 *       melden, auch nicht, indem es einen {@code locationId} in die Nutzdaten schreibt.</li>
 *   <li><b>Keine Quittung ohne Persistierung</b>: scheitert das Speichern, bekommt das Terminal
 *       {@code ERROR} statt {@code OFFLINE_INCIDENT_ACK} - sonst würde es die Meldung aus seiner
 *       Outbox entfernen und der Geldverlust verschwände unbemerkt.</li>
 * </ol>
 */
class TerminalWebSocketHandlerOfflineIncidentTest {

    private static final int SESSION_LOCATION_ID = 7;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TerminalOfflineIncidentService incidentService;
    private TerminalWebSocketHandler handler;
    private WebSocketSession session;
    private ConcurrentLinkedQueue<String> sent;

    @BeforeEach
    void setUp() {
        this.incidentService = mock(TerminalOfflineIncidentService.class);
        TerminalConnectionRegistry registry = mock(TerminalConnectionRegistry.class);
        // Die Registry liefert hier keine geschützte Session - der Handler fällt dann bewusst auf
        // die eigene Session zurück (siehe TerminalWebSocketHandler#send).
        when(registry.guardedSession(any(), any())).thenReturn(Optional.empty());
        this.handler = new TerminalWebSocketHandler(registry, this.objectMapper,
                mock(TerminalMaintenanceService.class), this.incidentService);

        this.sent = new ConcurrentLinkedQueue<>();
        this.session = mock(WebSocketSession.class);
        when(this.session.getId()).thenReturn("test-session");
        when(this.session.isOpen()).thenReturn(true);
        when(this.session.getAttributes()).thenReturn(new HashMap<>(Map.of(
                TerminalHandshakeInterceptor.ATTR_LOCATION_ID, SESSION_LOCATION_ID,
                TerminalHandshakeInterceptor.ATTR_LOCATION_NAME, "Kellerwaschkueche")));
    }

    /** Fängt die vom Handler gesendeten Nachrichten ab. */
    private void captureSends() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            WebSocketMessage<?> message = invocation.getArgument(0);
            this.sent.add(String.valueOf(message.getPayload()));
            return null;
        }).when(this.session).sendMessage(any());
    }

    private void handle(Map<String, Object> payload) throws Exception {
        captureSends();
        TerminalWsMessage message = TerminalWsMessage.of(TerminalWsMessageType.OFFLINE_INCIDENT, payload);
        this.handler.handleTextMessage(this.session,
                new TextMessage(this.objectMapper.writeValueAsString(message)));
    }

    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("incidentKey", "DEAD_LETTER:abc");
        payload.put("kind", "DEAD_LETTER");
        payload.put("entryType", "FINISH");
        payload.put("idempotencyKey", "abc");
        payload.put("chargedPrice", "1.50");
        payload.put("reason", "vom Backend endgueltig abgelehnt");
        payload.put("occurredAt", LocalDateTime.of(2026, 7, 24, 9, 30).toString());
        return payload;
    }

    @Test
    void theLocationComesFromTheSessionEvenIfThePayloadClaimsAnotherOne() throws Exception {
        Map<String, Object> payload = validPayload();
        // Angriffsversuch: das Terminal behauptet einen FREMDEN Standort in den Nutzdaten.
        payload.put("locationId", 999);

        handle(payload);

        // Gemeldet wird der Standort der token-geprüften Session, nicht der aus den Nutzdaten.
        verify(this.incidentService).report(eq(SESSION_LOCATION_ID), eq("DEAD_LETTER:abc"), eq("DEAD_LETTER"),
                eq("FINISH"), eq("abc"), eq(null), eq(new BigDecimal("1.50")),
                eq("vom Backend endgueltig abgelehnt"), eq(LocalDateTime.of(2026, 7, 24, 9, 30)));
        verify(this.incidentService, never()).report(eq(999), anyString(), anyString(), any(), any(), any(), any(),
                anyString(), any());
        assertThat(String.join("", this.sent)).contains("OFFLINE_INCIDENT_ACK");
    }

    @Test
    void aFailedPersistenceIsAnsweredWithErrorInsteadOfAnAck() throws Exception {
        when(this.incidentService.report(any(), anyString(), anyString(), any(), any(), any(), any(), anyString(),
                any())).thenThrow(new IllegalStateException("DB weg"));

        handle(validPayload());

        String responses = String.join("", this.sent);
        assertThat(responses).contains("incident-not-recorded");
        // Entscheidend: KEINE Quittung - sonst räumte das Terminal die Meldung aus seiner Outbox.
        assertThat(responses).doesNotContain("OFFLINE_INCIDENT_ACK");
    }

    @Test
    void anIncidentWithoutKeyOrKindIsRejectedWithoutTouchingTheService() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", "unvollstaendig");

        handle(payload);

        assertThat(String.join("", this.sent)).contains("invalid-incident");
        verify(this.incidentService, never()).report(any(), anyString(), anyString(), any(), any(), any(), any(),
                anyString(), any());
    }

    @Test
    void anAcknowledgementCarriesTheIncidentKeyBackToTheTerminal() throws Exception {
        handle(validPayload());

        // Die Outbox des Terminals entfernt genau anhand dieses Schlüssels - fehlt er, bliebe die
        // Meldung dort für immer liegen und würde bei jedem Reconnect erneut gesendet.
        List<String> responses = List.copyOf(this.sent);
        assertThat(responses).anySatisfy(r -> assertThat(r).contains("OFFLINE_INCIDENT_ACK")
                .contains("DEAD_LETTER:abc"));
    }
}
