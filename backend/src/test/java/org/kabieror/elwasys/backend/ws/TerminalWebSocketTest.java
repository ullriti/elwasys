package org.kabieror.elwasys.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.CharBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
