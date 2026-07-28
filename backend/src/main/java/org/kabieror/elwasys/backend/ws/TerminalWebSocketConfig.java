package org.kabieror.elwasys.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Registriert den Terminal-WebSocket-Endpunkt unter {@code /api/v1/terminal-ws} (AP4, siehe
 * docs/kb/05-migration-plan.md). Der Pfad liegt bewusst unter {@code /api/v1/**}, damit die
 * zustandslose Standort-Token-Sicherheitskette ({@code TerminalApiSecurityConfig}) auch für
 * den Handshake greift - siehe {@link TerminalHandshakeInterceptor}.
 *
 * <p>{@code @EnableScheduling} aktiviert den Heartbeat ({@link TerminalHeartbeatScheduler}) -
 * an dieser Stelle deklariert, weil er ausschließlich vom Terminal-WebSocket-Fundament
 * gebraucht wird.
 *
 * <p>{@code @Profile("!token-cli & !admin-cli")}: Spring Boots Standard-{@code TaskScheduler}
 * für {@code @Scheduled} nutzt NICHT-Daemon-Threads - ohne diesen Ausschluss würde ein
 * einmaliger CLI-Aufruf ({@code TerminalTokenCliRunner}, Profil {@code token-cli}; bzw.
 * {@code AdminPasswordCliRunner}, Profil {@code admin-cli}, seit Phase 5 AP2) nach getaner
 * Arbeit nicht beendet, weil der Heartbeat-Scheduler-Thread den Prozess am Leben hält
 * (gefunden beim manuellen Testen der CLI in AP4, beim admin-cli-Härtungs-Arbeitspaket
 * reproduziert). Der CLI-Modus braucht ohnehin weder WebSocket noch Heartbeat.
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@Profile("!token-cli & !admin-cli")
public class TerminalWebSocketConfig implements WebSocketConfigurer {

    public static final String TERMINAL_WS_PATH = "/api/v1/terminal-ws";

    /**
     * Frame-Grenze dieses Endpunkts (ADR 0024). Der JSR-356-Default von 8 KiB ist für dieses
     * Protokoll zu knapp: eine {@code LOG_RESPONSE} überschritt ihn schon nach wenigen Minuten
     * Terminal-Betrieb, woraufhin Tomcat die Verbindung mit {@code 1009} schloss und damit auch
     * Status/Neustart/Vorfallsmeldungen bis zum Reconnect mit abriss. Das Terminal deckelt seine
     * Log-Antwort seitdem zusätzlich an der Quelle ({@code TerminalWebSocketClient}) - dieser
     * Wert ist das Sicherheitsnetz und muss zu dem der Gegenstelle passen, weil jede Seite nur
     * ihren EIGENEN Empfangspuffer prüft.
     */
    private static final int MAX_TEXT_MESSAGE_BUFFER_BYTES = 1024 * 1024;

    private final TerminalConnectionRegistry connectionRegistry;

    private final ObjectMapper objectMapper;

    private final TerminalMaintenanceService maintenanceService;

    private final TerminalOfflineIncidentService incidentService;

    public TerminalWebSocketConfig(TerminalConnectionRegistry connectionRegistry, ObjectMapper objectMapper,
            TerminalMaintenanceService maintenanceService, TerminalOfflineIncidentService incidentService) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
        this.maintenanceService = maintenanceService;
        this.incidentService = incidentService;
    }

    @Bean
    public WebSocketHandler terminalWebSocketHandler() {
        return new TerminalWebSocketHandler(this.connectionRegistry, this.objectMapper, this.maintenanceService,
                this.incidentService);
    }

    /**
     * Hebt die Empfangs-Frame-Grenze des eingebetteten Servlet-Containers an (siehe
     * {@link #MAX_TEXT_MESSAGE_BUFFER_BYTES}). Betrifft nur Text-Frames - dieses Protokoll
     * kennt keine Binär-Frames.
     */
    @Bean
    public ServletServerContainerFactoryBean terminalWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_BYTES);
        return container;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler(), TERMINAL_WS_PATH)
                .addInterceptors(new TerminalHandshakeInterceptor());
    }
}
