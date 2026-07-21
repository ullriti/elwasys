package org.kabieror.elwasys.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * Portal-seitige Vermittlung von Fernwartungs-Anfragen (Status/Logs/Neustart) über den
 * bestehenden Terminal-WebSocket-Kanal (Phase 3 AP4, siehe kb/05-migration-plan.md,
 * Roadmap-Punkt "Fernwartung (Status/Logs/Restart über Backend-Kanal)"). Fachlicher
 * Nachfolger von {@code Portal/.../MaintenanceConnectionManager} + der
 * {@code IClientConnection#sendQuery}/{@code #sendCommand}-Aufrufe in
 * {@code Portal/.../views/AdminDashboardView} (Alt-Portal) - dort ein eigenes TCP-Protokoll
 * ({@code Common.maintenance.*}), hier Anfrage/Antwort über dieselbe WebSocket-Verbindung, die
 * Terminals laut Zielarchitektur ohnehin ausgehend zum Backend halten.
 *
 * <p><b>Bewusst NICHT portiert</b>: das Alt-TCP-Protokoll selbst
 * ({@code MaintenanceConnectionManager}/{@code MaintenanceServer}) - das Alt-Portal bleibt
 * dafür bis zum Cutover in Betrieb (siehe kb/05-migration-plan.md, "Entscheidungen"). Die
 * Alt-Clients verbinden sich außerdem erst in Phase 4 überhaupt mit dem neuen Backend-WS-Kanal
 * - in der Praxis ist ein Standort hier also i.d.R. NICHT verbunden
 * ({@link #requestLog}/{@link #requestRestart} werfen dann sofort
 * {@link TerminalNotConnectedException}, ohne eine Nachricht zu verschicken).
 *
 * <p><b>Vermittlungslogik</b>: sendet eine {@code LOG_REQUEST}/{@code RESTART_REQUEST} mit
 * einer vom Absender vergebenen Korrelations-Id ({@link TerminalWsMessage#id()}) und merkt sich
 * ein {@link CompletableFuture} unter dieser Id. Antwortet das Terminal mit derselben Id
 * (Feld {@code inReplyTo}, siehe {@link TerminalWebSocketHandler#handleTextMessage}), wird das
 * Future über {@link #completeIfPending} erfüllt. Antwortet niemand innerhalb von
 * {@link #REQUEST_TIMEOUT}, wird das Future mit {@link TerminalRequestTimeoutException}
 * fehlgeschlagen - die Portal-UI blockiert dadurch nie unbegrenzt auf ein nicht
 * antwortendes/veraltetes Terminal.
 */
@Component
public class TerminalMaintenanceService {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalMaintenanceService.class);

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final TerminalConnectionRegistry connectionRegistry;

    private final ObjectMapper objectMapper;

    private final Map<String, CompletableFuture<TerminalWsMessage>> pendingRequests = new ConcurrentHashMap<>();

    private final Map<String, Integer> pendingRequestLocations = new ConcurrentHashMap<>();

    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terminal-maintenance-timeout");
        thread.setDaemon(true);
        return thread;
    });

    public TerminalMaintenanceService(TerminalConnectionRegistry connectionRegistry, ObjectMapper objectMapper) {
        this.connectionRegistry = connectionRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Ob das Terminal eines Standorts aktuell verbunden ist - für die Portal-UI, um den in
     * der Aufgabenstellung geforderten klaren Zustand ("Terminal nicht verbunden") ohne
     * Zeitüberschreitung anzuzeigen.
     */
    public boolean isConnected(Integer locationId) {
        return this.connectionRegistry.isConnected(locationId);
    }

    public Optional<Instant> connectedSince(Integer locationId) {
        return this.connectionRegistry.connectedSince(locationId);
    }

    /**
     * Fordert den aktuellen Log-Inhalt des Terminals eines Standorts an - fachlicher
     * Nachfolger des Log-Knopfs im Alt-Dashboard ({@code GetLogRequest}/{@code GetLogResponse}).
     *
     * @throws TerminalNotConnectedException  wenn der Standort nicht verbunden ist
     * @throws TerminalRequestTimeoutException wenn das Terminal nicht rechtzeitig antwortet
     */
    public List<String> requestLog(Integer locationId) {
        TerminalWsMessage response = sendAndAwait(locationId, TerminalWsMessageType.LOG_REQUEST, Map.of());
        Object lines = response.payload() == null ? null : response.payload().get("lines");
        if (lines instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Fordert einen Neustart der Client-Anwendung am Standort an - fachlicher Nachfolger des
     * Neustart-Menüpunkts im Alt-Dashboard ({@code RestartAppRequest}). Anders als der
     * Alt-Code (dort "fire-and-forget", die Erfolgsmeldung wird sofort nach dem Verschicken
     * angezeigt) wartet diese Methode auf eine Bestätigung ({@code RESTART_RESPONSE}) des
     * Terminals - eine bewusste UX-Verbesserung (der Admin erfährt zuverlässig, ob der Befehl
     * überhaupt ankam), ermöglicht durch das ohnehin für {@link #requestLog} gebrauchte
     * Anfrage-/Antwort-Muster.
     *
     * @throws TerminalNotConnectedException  wenn der Standort nicht verbunden ist
     * @throws TerminalRequestTimeoutException wenn das Terminal nicht rechtzeitig bestätigt
     */
    public void requestRestart(Integer locationId) {
        sendAndAwait(locationId, TerminalWsMessageType.RESTART_REQUEST, Map.of());
    }

    /**
     * Wird vom {@link TerminalWebSocketHandler} für jede eingehende {@code LOG_RESPONSE}/
     * {@code RESTART_RESPONSE} aufgerufen: erfüllt ein wartendes {@link #sendAndAwait}, falls
     * die Korrelations-Id ({@code inReplyTo}) zu einer offenen Anfrage passt. Nachrichten ohne
     * (mehr) passende Anfrage (z.B. nach Timeout bereits entfernt) werden ignoriert.
     */
    public void completeIfPending(TerminalWsMessage message) {
        if (message.id() == null) {
            return;
        }
        CompletableFuture<TerminalWsMessage> future = this.pendingRequests.remove(message.id());
        this.pendingRequestLocations.remove(message.id());
        if (future != null) {
            future.complete(message);
        }
    }

    private TerminalWsMessage sendAndAwait(Integer locationId, TerminalWsMessageType type,
            Map<String, Object> payload) {
        if (!this.connectionRegistry.isConnected(locationId)) {
            throw new TerminalNotConnectedException(locationId);
        }

        TerminalWsMessage request = TerminalWsMessage.of(type, payload);
        CompletableFuture<TerminalWsMessage> future = new CompletableFuture<>();
        this.pendingRequests.put(request.id(), future);
        this.pendingRequestLocations.put(request.id(), locationId);

        try {
            boolean sent = this.connectionRegistry.send(locationId,
                    new TextMessage(this.objectMapper.writeValueAsString(request)));
            if (!sent) {
                this.pendingRequests.remove(request.id());
                this.pendingRequestLocations.remove(request.id());
                throw new TerminalNotConnectedException(locationId);
            }
        } catch (IOException e) {
            this.pendingRequests.remove(request.id());
            this.pendingRequestLocations.remove(request.id());
            throw new TerminalNotConnectedException(locationId);
        }

        this.timeoutExecutor.schedule(() -> {
            CompletableFuture<TerminalWsMessage> timedOut = this.pendingRequests.remove(request.id());
            this.pendingRequestLocations.remove(request.id());
            if (timedOut != null) {
                timedOut.completeExceptionally(new TerminalRequestTimeoutException(locationId));
            }
        }, REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        try {
            return future.get(REQUEST_TIMEOUT.toMillis() + 1000, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new TerminalRequestTimeoutException(locationId);
        } catch (InterruptedException | java.util.concurrent.TimeoutException e) {
            Thread.currentThread().interrupt();
            this.pendingRequests.remove(request.id());
            this.pendingRequestLocations.remove(request.id());
            throw new TerminalRequestTimeoutException(locationId);
        }
    }

    @PreDestroy
    void shutdown() {
        this.timeoutExecutor.shutdownNow();
        LOG.debug("Terminal maintenance timeout executor shut down.");
    }
}
