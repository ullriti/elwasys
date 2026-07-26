package org.kabieror.elwasys.backend.ws;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Verbindungsregistry für die ausgehenden Terminal-WebSocket-Verbindungen (AP4, siehe
 * docs/kb/05-migration-plan.md: "Kanal-Fundament ... Verbindungs-Registry (welcher Standort ist
 * verbunden)"). Ersetzt fachlich die alte {@code client_ip}/{@code client_port}-Registrierung
 * in {@code locations} (siehe docs/kb/02-data-model.md, "Weg - obsolet durch ausgehende
 * Client-Verbindung") - hier rein in-memory, weil die Verbindung selbst (nicht ein
 * gespeicherter Endpunkt) die Erreichbarkeit repräsentiert.
 *
 * <p>Genau eine aktive Session pro Standort: verbindet sich ein Terminal erneut (z.B. nach
 * Netz-Schluckauf), bevor die alte Session als tot erkannt wurde, wird die alte Session
 * geschlossen und durch die neue ersetzt - ein Standort kann nicht "doppelt" verbunden sein.
 *
 * <p>Alle Methoden erwarten den Standort explizit (statt über die Session-Id in der
 * Verbindungstabelle zu suchen) - der Aufrufer ({@link TerminalWebSocketHandler}) kennt ihn
 * bereits aus den beim Handshake gesetzten Session-Attributen (siehe
 * {@link TerminalHandshakeInterceptor}).
 */
@Component
public class TerminalConnectionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalConnectionRegistry.class);

    /**
     * Obergrenze, wie lange ein Sendevorgang auf einen langsamen/nicht lesenden Client warten
     * darf, bevor dessen Session geschlossen wird (siehe
     * {@link ConcurrentWebSocketSessionDecorator}). Bewusst knapp: die Nachrichten hier sind
     * klein (Heartbeat, Fernwartung, Vorfalls-Quittungen), ein Terminal, das sie 10 s lang nicht
     * abnimmt, ist praktisch weg - dann ist Abräumen besser als einen Server-Thread zu binden.
     */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    /**
     * Puffergrenze je Verbindung. Reicht für einen Schwall Vorfalls-Quittungen unmittelbar nach
     * einem Reconnect (die Outbox eines Terminals fasst 100 Meldungen) samt Heartbeat.
     */
    private static final int SEND_BUFFER_SIZE_BYTES = 512 * 1024;

    private static final class Connection {
        private final WebSocketSession session;
        private final Instant connectedSince;
        private volatile Instant lastPongAt;

        private Connection(WebSocketSession session) {
            this.session = session;
            this.connectedSince = Instant.now();
            this.lastPongAt = Instant.now();
        }
    }

    private final Map<Integer, Connection> connectionsByLocationId = new ConcurrentHashMap<>();

    public void register(Integer locationId, WebSocketSession session) {
        // Session in einen ConcurrentWebSocketSessionDecorator wickeln und AUSSCHLIESSLICH diese
        // Instanz weiterreichen: auf dieselbe Verbindung schreiben inzwischen drei Threads - der
        // WebSocket-Thread (Antworten/ACKs), der Portal-/Vaadin-Thread (Fernwartungsanfragen über
        // send()) und der Heartbeat-Scheduler (PING). Eine WebSocketSession vertraegt KEINE
        // parallelen Schreibvorgaenge; eine Kollision quittiert Tomcat mit
        // "IllegalStateException: The remote endpoint was in state [TEXT_FULL_WRITING]" - dann
        // ginge z.B. die Quittung eines Offline-Vorfalls verloren und sein Alarm bliebe haengen.
        // Der Decorator serialisiert die Sendevorgaenge und puffert sie; ein Client, der nicht
        // liest, laeuft in die Limits, statt den sendenden Thread dauerhaft zu blockieren.
        WebSocketSession guarded = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS,
                SEND_BUFFER_SIZE_BYTES);
        Connection previous = this.connectionsByLocationId.put(locationId, new Connection(guarded));
        if (previous != null && previous.session.isOpen() && !previous.session.getId().equals(session.getId())) {
            LOG.info("Location {} reconnected - closing previous session {}.", locationId, previous.session.getId());
            closeQuietly(previous.session, CloseStatus.NORMAL.withReason("Replaced by a newer connection"));
        }
    }

    public void unregister(Integer locationId, WebSocketSession session) {
        this.connectionsByLocationId.computeIfPresent(locationId, (id, connection) -> {
            if (connection.session.getId().equals(session.getId())) {
                return null;
            }
            return connection;
        });
    }

    public void markPong(Integer locationId, WebSocketSession session) {
        Connection connection = this.connectionsByLocationId.get(locationId);
        if (connection != null && connection.session.getId().equals(session.getId())) {
            connection.lastPongAt = Instant.now();
        }
    }

    public boolean isConnected(Integer locationId) {
        Connection connection = this.connectionsByLocationId.get(locationId);
        return connection != null && connection.session.isOpen();
    }

    /**
     * Sendet eine Nachricht an das aktuell verbundene Terminal eines Standorts - Grundlage
     * für {@link TerminalMaintenanceService} (Phase 3 AP4, portal-initiierte Fernwartungs-
     * Anfragen wie Log/Neustart). Kapselt den Zugriff auf {@link WebSocketSession} bewusst
     * hier (statt die Session selbst nach außen zu geben), damit "eine Verbindung pro
     * Standort" ein Implementierungsdetail dieser Klasse bleibt.
     *
     * @return {@code true}, wenn eine offene Verbindung gefunden und die Nachricht
     *         übergeben wurde; {@code false}, wenn der Standort nicht (mehr) verbunden ist
     */
    public boolean send(Integer locationId, org.springframework.web.socket.WebSocketMessage<?> message)
            throws IOException {
        Connection connection = this.connectionsByLocationId.get(locationId);
        if (connection == null || !connection.session.isOpen()) {
            return false;
        }
        connection.session.sendMessage(message);
        return true;
    }

    /**
     * Die serialisierungsgeschützte Session eines Standorts (siehe {@link #register}) - für den
     * {@link TerminalWebSocketHandler}, der seine Antworten sonst auf der ROHEN, ungeschützten
     * Session verschicken würde und damit an der Serialisierung vorbeischriebe.
     *
     * @return leer, wenn der Standort nicht (mehr) verbunden ist oder eine ANDERE Session
     *         registriert ist (z.B. nach einem Reconnect) - der Aufrufer nutzt dann seine eigene
     *         Session, damit eine Antwort im Zweifel eher rausgeht als gar nicht
     */
    Optional<WebSocketSession> guardedSession(Integer locationId, WebSocketSession session) {
        Connection connection = this.connectionsByLocationId.get(locationId);
        if (connection == null || !connection.session.getId().equals(session.getId())) {
            return Optional.empty();
        }
        return Optional.of(connection.session);
    }

    public Optional<Instant> connectedSince(Integer locationId) {
        return Optional.ofNullable(this.connectionsByLocationId.get(locationId)).map(c -> c.connectedSince);
    }

    public Set<Integer> connectedLocationIds() {
        return Set.copyOf(this.connectionsByLocationId.keySet());
    }

    /**
     * Sendet allen aktuell verbundenen Terminals ein PING (siehe
     * {@code TerminalHeartbeatScheduler}) und schließt Verbindungen, deren letztes PONG
     * länger als {@code timeout} zurückliegt.
     */
    void pingAndReapStale(Duration timeout, java.util.function.Consumer<WebSocketSession> pingAction) {
        Instant threshold = Instant.now().minus(timeout);
        for (Map.Entry<Integer, Connection> entry : this.connectionsByLocationId.entrySet()) {
            Connection connection = entry.getValue();
            if (!connection.session.isOpen()) {
                continue;
            }
            if (connection.lastPongAt.isBefore(threshold)) {
                LOG.warn("Location {} did not respond to heartbeat within {} - closing connection {}.", entry.getKey(),
                        timeout, connection.session.getId());
                closeQuietly(connection.session, CloseStatus.GOING_AWAY.withReason("Heartbeat timeout"));
                continue;
            }
            pingAction.accept(connection.session);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            LOG.debug("Failed to close a stale/replaced terminal WebSocket session.", e);
        }
    }
}
