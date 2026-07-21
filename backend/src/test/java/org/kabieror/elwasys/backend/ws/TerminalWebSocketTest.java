package org.kabieror.elwasys.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.kabieror.elwasys.backend.support.TestPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-End-Test des Terminal-WebSocket-Endpunkts ({@code /api/v1/terminal-ws}, AP4, siehe
 * kb/05-migration-plan.md/kb/03-modules.md): Handshake mit/ohne Standort-Token sowie das
 * Minimal-Nachrichtenpaar (HELLO/HELLO_ACK, PING/PONG, STATUS_REQUEST/STATUS_RESPONSE).
 *
 * <p>Nutzt den JDK-eigenen {@link java.net.http.HttpClient}-WebSocket-Client (keine
 * zusätzliche Testabhängigkeit nötig) - derselbe Client, den der Raspi-Terminal-Client laut
 * kb/05-migration-plan.md ohnehin für die Backend-Anbindung nutzen soll
 * (Technologie-Entscheidung "HTTP im Terminal").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TerminalWebSocketTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TerminalTokenService terminalTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TerminalMaintenanceService maintenanceService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        this.executor.shutdownNow();
    }

    private URI wsUri() {
        return URI.create("ws://localhost:" + this.port + "/api/v1/terminal-ws");
    }

    private static class RecordingListener implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.buffer.append(data);
            if (last) {
                this.messages.add(this.buffer.toString());
                this.buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        String next() throws InterruptedException {
            String message = this.messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("expected a WebSocket message within 5s").isNotNull();
            return message;
        }
    }

    @Test
    void handshakeWithoutTokenIsRejected() {
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                .buildAsync(wsUri(), new RecordingListener());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void handshakeWithInvalidTokenIsRejected() {
        HttpClient client = HttpClient.newHttpClient();
        CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                .header("Authorization", "Bearer elwt_not-a-real-token")
                .buildAsync(wsUri(), new RecordingListener());

        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void helloReceivesHelloAckWithLocationContext() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        RecordingListener listener = new RecordingListener();
        WebSocket ws = connect(token, listener);
        try {
            String helloId = "hello-1";
            sendMessage(ws, new TerminalWsMessage(1, TerminalWsMessageType.HELLO, helloId,
                    Map.of("clientVersion", "test-client")));

            TerminalWsMessage ack = this.objectMapper.readValue(listener.next(), TerminalWsMessage.class);
            assertThat(ack.type()).isEqualTo(TerminalWsMessageType.HELLO_ACK);
            assertThat(ack.id()).isEqualTo(helloId);
            assertThat(((Number) ack.payload().get("locationId")).intValue()).isEqualTo(location.getId());
            assertThat(ack.payload().get("locationName")).isEqualTo(location.getName());
        } finally {
            ws.abort();
        }
    }

    @Test
    void pingReceivesPong() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        RecordingListener listener = new RecordingListener();
        WebSocket ws = connect(token, listener);
        try {
            sendMessage(ws, TerminalWsMessage.of(TerminalWsMessageType.PING, Map.of()));

            TerminalWsMessage pong = this.objectMapper.readValue(listener.next(), TerminalWsMessage.class);
            assertThat(pong.type()).isEqualTo(TerminalWsMessageType.PONG);
        } finally {
            ws.abort();
        }
    }

    @Test
    void statusRequestReceivesStatusResponse() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        RecordingListener listener = new RecordingListener();
        WebSocket ws = connect(token, listener);
        try {
            sendMessage(ws, TerminalWsMessage.of(TerminalWsMessageType.STATUS_REQUEST, Map.of()));

            TerminalWsMessage response = this.objectMapper.readValue(listener.next(), TerminalWsMessage.class);
            assertThat(response.type()).isEqualTo(TerminalWsMessageType.STATUS_RESPONSE);
            assertThat(((Number) response.payload().get("locationId")).intValue()).isEqualTo(location.getId());
        } finally {
            ws.abort();
        }
    }

    @Test
    void unimplementedMessageTypeReceivesAnErrorReply() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        RecordingListener listener = new RecordingListener();
        WebSocket ws = connect(token, listener);
        try {
            sendMessage(ws, TerminalWsMessage.of(TerminalWsMessageType.LOG_REQUEST, Map.of()));

            TerminalWsMessage response = this.objectMapper.readValue(listener.next(), TerminalWsMessage.class);
            assertThat(response.type()).isEqualTo(TerminalWsMessageType.ERROR);
        } finally {
            ws.abort();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Phase 3 AP4 (siehe kb/05-migration-plan.md, Roadmap-Punkt "Fernwartung"): Tests der
    // Portal-seitigen Fernwartungs-Vermittlung (TerminalMaintenanceService) - ergänzt HIER
    // (statt in einer eigenen Testklasse) bewusst denselben RANDOM_PORT-Kontext wieder, um
    // keinen weiteren, zusätzlichen Spring-Kontext (samt eigenem Connection-Pool) gegen den
    // gemeinsam genutzten Test-Postgres-Cluster zu öffnen (in dieser Sandbox mit begrenzten
    // max_connections beobachtet). "TerminalMaintenanceService" ist die Portal-seitige
    // Vermittlung, KEIN echter Terminal-Handler - da sich Alt-Clients laut Roadmap ERST in
    // Phase 4 über diesen Kanal verbinden, simuliert {@link SimulatedTerminal} hier die
    // (noch nicht existierende) Terminal-Gegenstelle.
    // ---------------------------------------------------------------------------------------

    /**
     * Simulierter Terminal-WS-Client: reicht jede eingehende Nachricht an eine Queue durch
     * und kann - anders als der reine Beobachter {@link RecordingListener} oben - über
     * {@link #reply} selbst Nachrichten zurücksenden.
     */
    private static class SimulatedTerminal implements WebSocket.Listener {
        private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            this.buffer.append(data);
            if (last) {
                this.messages.add(this.buffer.toString());
                this.buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        String next() throws InterruptedException {
            String message = this.messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).as("expected the server to send a request within 5s").isNotNull();
            return message;
        }
    }

    private WebSocket connectSimulatedTerminal(IssuedTerminalToken token, SimulatedTerminal listener)
            throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        return client.newWebSocketBuilder().header("Authorization", "Bearer " + token.rawToken())
                .buildAsync(wsUri(), listener).get(5, TimeUnit.SECONDS);
    }

    private void reply(WebSocket ws, TerminalWsMessage message) throws Exception {
        ws.sendText(CharBuffer.wrap(this.objectMapper.writeValueAsString(message)), true).get(5, TimeUnit.SECONDS);
    }

    /**
     * Der Client-Handshake ({@link #connectSimulatedTerminal}) gilt bereits als abgeschlossen,
     * bevor der Server die Verbindung in seiner Registry eingetragen hat
     * ({@code afterConnectionEstablished} läuft asynchron dazu) - ein sofortiges
     * {@code isConnected(...)} direkt nach dem Connect ist daher ein Wettlauf, den langsame
     * CI-Runner verlieren können. Deshalb: bis zu 5s auf die serverseitige Registrierung warten.
     */
    private void awaitConnected(Integer locationId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!this.maintenanceService.isConnected(locationId)) {
            if (System.nanoTime() > deadline) {
                break;
            }
            Thread.sleep(25);
        }
        assertThat(this.maintenanceService.isConnected(locationId))
                .as("expected the server to register the terminal connection within 5s").isTrue();
    }

    @Test
    void maintenanceRequestOnANotConnectedLocationFailsImmediatelyWithoutTimeout() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));

        long start = System.nanoTime();
        assertThatThrownBy(() -> this.maintenanceService.requestLog(location.getId())).isInstanceOf(
                TerminalNotConnectedException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).as("must fail fast, not wait for the request timeout").isLessThan(2000);
        assertThat(this.maintenanceService.isConnected(location.getId())).isFalse();

        assertThatThrownBy(() -> this.maintenanceService.requestRestart(location.getId())).isInstanceOf(
                TerminalNotConnectedException.class);
    }

    @Test
    void requestLogReturnsTheLinesFromTheSimulatedTerminal() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        SimulatedTerminal terminal = new SimulatedTerminal();
        WebSocket ws = connectSimulatedTerminal(token, terminal);
        try {
            awaitConnected(location.getId());

            Future<List<String>> future = this.executor.submit(
                    () -> this.maintenanceService.requestLog(location.getId()));

            TerminalWsMessage request = this.objectMapper.readValue(terminal.next(), TerminalWsMessage.class);
            assertThat(request.type()).isEqualTo(TerminalWsMessageType.LOG_REQUEST);

            reply(ws, TerminalWsMessage.inReplyTo(request, TerminalWsMessageType.LOG_RESPONSE,
                    Map.of("lines", List.of("line one", "line two"))));

            List<String> lines = future.get(5, TimeUnit.SECONDS);
            assertThat(lines).containsExactly("line one", "line two");
        } finally {
            ws.abort();
        }
    }

    @Test
    void requestStatusReturnsThePayloadFromTheSimulatedTerminal() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        SimulatedTerminal terminal = new SimulatedTerminal();
        WebSocket ws = connectSimulatedTerminal(token, terminal);
        try {
            awaitConnected(location.getId());

            Future<Map<String, Object>> future = this.executor.submit(
                    () -> this.maintenanceService.requestStatus(location.getId()));

            TerminalWsMessage request = this.objectMapper.readValue(terminal.next(), TerminalWsMessage.class);
            assertThat(request.type()).isEqualTo(TerminalWsMessageType.STATUS_REQUEST);

            reply(ws, TerminalWsMessage.inReplyTo(request, TerminalWsMessageType.STATUS_RESPONSE,
                    Map.of("clientVersion", "test-client-version", "runningExecutionIds", List.of())));

            Map<String, Object> status = future.get(5, TimeUnit.SECONDS);
            assertThat(status.get("clientVersion")).isEqualTo("test-client-version");
        } finally {
            ws.abort();
        }
    }

    @Test
    void requestRestartCompletesOnceTheSimulatedTerminalAcknowledges() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        SimulatedTerminal terminal = new SimulatedTerminal();
        WebSocket ws = connectSimulatedTerminal(token, terminal);
        try {
            awaitConnected(location.getId());

            Future<?> future = this.executor.submit(() -> this.maintenanceService.requestRestart(location.getId()));

            TerminalWsMessage request = this.objectMapper.readValue(terminal.next(), TerminalWsMessage.class);
            assertThat(request.type()).isEqualTo(TerminalWsMessageType.RESTART_REQUEST);

            reply(ws, TerminalWsMessage.inReplyTo(request, TerminalWsMessageType.RESTART_RESPONSE,
                    Map.of("accepted", true)));

            // Wirft nicht, wenn erfolgreich (get() gibt null zurück, da requestRestart void ist).
            future.get(5, TimeUnit.SECONDS);
        } finally {
            ws.abort();
        }
    }

    @Test
    void requestLogTimesOutIfTheConnectedTerminalNeverReplies() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, null);

        SimulatedTerminal terminal = new SimulatedTerminal();
        WebSocket ws = connectSimulatedTerminal(token, terminal);
        try {
            awaitConnected(location.getId());

            Future<List<String>> future = this.executor.submit(
                    () -> this.maintenanceService.requestLog(location.getId()));

            // Beweist, dass die Anfrage tatsächlich beim (simulierten) Terminal ankam - es
            // antwortet nur absichtlich nicht (spielt den in Phase 3 realistischen Fall eines
            // verbundenen, aber das Nachrichtenformat noch nicht implementierenden Terminals).
            TerminalWsMessage request = this.objectMapper.readValue(terminal.next(), TerminalWsMessage.class);
            assertThat(request.type()).isEqualTo(TerminalWsMessageType.LOG_REQUEST);

            assertThatThrownBy(() -> {
                try {
                    future.get(15, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    throw e.getCause();
                }
            }).isInstanceOf(TerminalRequestTimeoutException.class);
        } finally {
            ws.abort();
        }
    }

    private WebSocket connect(IssuedTerminalToken token, RecordingListener listener)
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        HttpClient client = HttpClient.newHttpClient();
        return client.newWebSocketBuilder().header("Authorization", "Bearer " + token.rawToken())
                .buildAsync(wsUri(), listener).get(5, TimeUnit.SECONDS);
    }

    private void sendMessage(WebSocket ws, TerminalWsMessage message) throws Exception {
        ws.sendText(CharBuffer.wrap(this.objectMapper.writeValueAsString(message)), true).get(5, TimeUnit.SECONDS);
    }
}
