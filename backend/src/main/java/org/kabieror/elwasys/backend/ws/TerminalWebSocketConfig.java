package org.kabieror.elwasys.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registriert den Terminal-WebSocket-Endpunkt unter {@code /api/v1/terminal-ws} (AP4, siehe
 * kb/05-migration-plan.md). Der Pfad liegt bewusst unter {@code /api/v1/**}, damit die
 * zustandslose Standort-Token-Sicherheitskette ({@code TerminalApiSecurityConfig}) auch für
 * den Handshake greift - siehe {@link TerminalHandshakeInterceptor}.
 *
 * <p>{@code @EnableScheduling} aktiviert den Heartbeat ({@link TerminalHeartbeatScheduler}) -
 * an dieser Stelle deklariert, weil er ausschließlich vom Terminal-WebSocket-Fundament
 * gebraucht wird.
 *
 * <p>{@code @Profile("!token-cli")}: Spring Boots Standard-{@code TaskScheduler} für
 * {@code @Scheduled} nutzt NICHT-Daemon-Threads - ohne diesen Ausschluss würde der
 * einmalige CLI-Aufruf ({@code TerminalTokenCliRunner}, Profil {@code token-cli}) nach
 * getaner Arbeit nicht beendet, weil der Heartbeat-Scheduler-Thread den Prozess am Leben
 * hält (gefunden beim manuellen Testen der CLI in AP4). Der CLI-Modus braucht ohnehin weder
 * WebSocket noch Heartbeat.
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@Profile("!token-cli")
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    public static final String TERMINAL_WS_PATH = "/api/v1/terminal-ws";

    private final TerminalConnectionRegistry connectionRegistry;

    private final ObjectMapper objectMapper;

    private final TerminalMaintenanceService maintenanceService;

    public TerminalWebSocketConfig(TerminalConnectionRegistry connectionRegistry, ObjectMapper objectMapper,
            TerminalMaintenanceService maintenanceService) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
        this.maintenanceService = maintenanceService;
    }

    @Bean
    public WebSocketHandler terminalWebSocketHandler() {
        return new TerminalWebSocketHandler(this.connectionRegistry, this.objectMapper, this.maintenanceService);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler(), TERMINAL_WS_PATH)
                .addInterceptors(new TerminalHandshakeInterceptor());
    }
}
