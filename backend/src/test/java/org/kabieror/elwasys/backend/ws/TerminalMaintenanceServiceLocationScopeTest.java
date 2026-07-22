package org.kabieror.elwasys.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

/**
 * Standort-Validierung der Fernwartungs-Antworten (Issue #26, Pre-Launch AP4): eine Antwort
 * darf ein wartendes {@code CompletableFuture} nur erfüllen, wenn ihr Absender-Standort mit dem
 * Ziel-Standort der ursprünglichen Anfrage übereinstimmt. Eine Antwort von einem FREMDEN
 * Standort (mit korrekt geratener Korrelations-Id) muss ignoriert werden.
 *
 * <p>Reiner Unit-Test: die {@link TerminalConnectionRegistry} wird gemockt (kein echter
 * WebSocket nötig), die abgeschickte Anfrage wird abgefangen, um ihre Korrelations-Id zu
 * bestimmen. Die beiden {@code completeIfPending}-Aufrufe erfolgen deterministisch in fester
 * Reihenfolge – ohne den Fix würde die fremde Antwort das Future fälschlich (mit den falschen
 * Daten) erfüllen.
 */
class TerminalMaintenanceServiceLocationScopeTest {

    private static final Integer TARGET_LOCATION = 1;
    private static final Integer FOREIGN_LOCATION = 2;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        this.executor.shutdownNow();
    }

    @Test
    void aReplyFromAForeignLocationDoesNotFulfillThePendingRequest() throws Exception {
        TerminalConnectionRegistry registry = mock(TerminalConnectionRegistry.class);
        when(registry.isConnected(TARGET_LOCATION)).thenReturn(true);

        // Die vom Service abgeschickte Anfrage abfangen, um ihre Korrelations-Id zu erhalten.
        AtomicReference<String> sentJson = new AtomicReference<>();
        when(registry.send(eq(TARGET_LOCATION), any())).thenAnswer(invocation -> {
            sentJson.set(((TextMessage) invocation.getArgument(1)).getPayload());
            return true;
        });

        TerminalMaintenanceService service = new TerminalMaintenanceService(registry, this.objectMapper);

        Future<List<String>> result = this.executor.submit(() -> service.requestLog(TARGET_LOCATION));

        String requestJson = awaitSentRequest(sentJson);
        TerminalWsMessage request = this.objectMapper.readValue(requestJson, TerminalWsMessage.class);
        assertThat(request.type()).isEqualTo(TerminalWsMessageType.LOG_REQUEST);

        // Fremder Standort antwortet mit derselben Id -> MUSS ignoriert werden.
        service.completeIfPending(FOREIGN_LOCATION, TerminalWsMessage.inReplyTo(request,
                TerminalWsMessageType.LOG_RESPONSE, Map.of("lines", List.of("from-foreign-location"))));

        // Der eigentliche Standort antwortet -> erfüllt das Future.
        service.completeIfPending(TARGET_LOCATION, TerminalWsMessage.inReplyTo(request,
                TerminalWsMessageType.LOG_RESPONSE, Map.of("lines", List.of("from-target-location"))));

        List<String> lines = result.get(5, TimeUnit.SECONDS);
        assertThat(lines).as("the foreign-location reply must be ignored; only the target reply counts")
                .containsExactly("from-target-location");
    }

    private static String awaitSentRequest(AtomicReference<String> sentJson) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sentJson.get() == null) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("the maintenance request was never sent");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return sentJson.get();
    }
}
