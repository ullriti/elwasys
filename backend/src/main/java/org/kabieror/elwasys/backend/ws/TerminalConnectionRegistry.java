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

/**
 * Verbindungsregistry für die ausgehenden Terminal-WebSocket-Verbindungen (AP4, siehe
 * kb/05-migration-plan.md: "Kanal-Fundament ... Verbindungs-Registry (welcher Standort ist
 * verbunden)"). Ersetzt fachlich die alte {@code client_ip}/{@code client_port}-Registrierung
 * in {@code locations} (siehe kb/02-data-model.md, "Weg - obsolet durch ausgehende
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
        Connection previous = this.connectionsByLocationId.put(locationId, new Connection(session));
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
