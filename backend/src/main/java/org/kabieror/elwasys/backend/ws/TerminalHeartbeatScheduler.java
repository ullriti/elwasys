package org.kabieror.elwasys.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * Heartbeat für die Terminal-WebSocket-Verbindungen (AP4, siehe kb/05-migration-plan.md:
 * "Ping/Heartbeat"). Sendet allen verbundenen Terminals periodisch ein PING und schließt
 * Verbindungen, die zu lange nicht mehr geantwortet haben (tote/hängende TCP-Verbindungen,
 * die der Server sonst erst durch einen fehlgeschlagenen Sendeversuch bemerken würde).
 */
@Component
public class TerminalHeartbeatScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalHeartbeatScheduler.class);

    private static final Duration PONG_TIMEOUT = Duration.ofSeconds(90);

    private final TerminalConnectionRegistry connectionRegistry;

    private final ObjectMapper objectMapper;

    public TerminalHeartbeatScheduler(TerminalConnectionRegistry connectionRegistry, ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRate = 30_000)
    public void pingConnectedTerminals() {
        this.connectionRegistry.pingAndReapStale(PONG_TIMEOUT, session -> {
            try {
                TerminalWsMessage ping = TerminalWsMessage.of(TerminalWsMessageType.PING, Map.of());
                session.sendMessage(new TextMessage(this.objectMapper.writeValueAsString(ping)));
            } catch (IOException e) {
                LOG.debug("Failed to send heartbeat PING to terminal WebSocket session {}.", session.getId(), e);
            }
        });
    }
}
