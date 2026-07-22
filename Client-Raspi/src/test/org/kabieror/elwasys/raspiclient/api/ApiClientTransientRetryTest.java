package org.kabieror.elwasys.raspiclient.api;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.api.dto.LocationDto;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for the CI flakiness diagnosed under Phase 4 (see docs/kb/05-migration-plan.md,
 * Änderungslog "Phase 4 CI-Stabilität"): {@code ClientAutoEndDeconzE2ETest} failed
 * deterministically in two consecutive CI runs with
 * {@code ApiException: Das Backend ist nicht erreichbar: HTTP/1.1 header parser received no
 * bytes} (cause chain: {@code IOException} -> {@code EOFException}), thrown out of
 * {@code ElwaManager#initiate()} during startup and tipping the terminal into the
 * {@code ERROR} state instead of {@code SELECT_DEVICE} - never reproduced locally under
 * normal conditions, matching the well-known java.net.http behaviour where a pooled
 * keep-alive connection that the server has already closed is still handed out for reuse (a
 * write succeeds into the half-closed socket, but the read then immediately hits EOF).
 * <p>
 * This test proves {@link ApiClient}'s single retry on exactly that transient I/O failure
 * class masks the symptom without hiding a genuinely unreachable backend: a bare-bones TCP
 * server closes the FIRST accepted connection immediately, without ever writing a response
 * (reproducing the "accepted, then closed with no bytes sent" failure), and answers correctly
 * on the SECOND connection. Retrying is safe here because the request under test ({@code GET
 * .../locations/me}) is idempotent (see the {@link ApiClient} class comment; mutating calls
 * carry an {@code Idempotency-Key} for the same reason).
 */
class ApiClientTransientRetryTest {

    @Test
    void get_retries_once_after_a_connection_closed_without_a_response() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            AtomicInteger connectionCount = new AtomicInteger();
            Thread serverThread = new Thread(() -> {
                try {
                    // First connection: accept, then close immediately without reading or
                    // writing anything - simulates a stale/already-closed keep-alive
                    // connection being handed to the client for reuse.
                    try (Socket first = serverSocket.accept()) {
                        connectionCount.incrementAndGet();
                    }

                    // Second connection (the retry): read the request and answer correctly.
                    try (Socket second = serverSocket.accept()) {
                        connectionCount.incrementAndGet();
                        readHttpRequestHeaders(second);
                        String body = "{\"id\":1,\"name\":\"Default\"}";
                        String response = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/json\r\n"
                                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                                + body;
                        second.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                        second.getOutputStream().flush();
                    }
                } catch (IOException e) {
                    // Best effort; a missing/failed response surfaces as an assertion failure
                    // below.
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ApiClient apiClient = new ApiClient("http://localhost:" + port + "/", "test-token");
            LocationDto location = apiClient.getMyLocation();

            assertNotNull(location, "The retried request should have returned a location");
            assertEquals("Default", location.name());
            assertEquals(2, connectionCount.get(),
                    "ApiClient should have opened exactly two TCP connections: the failed one and the retry");

            serverThread.join(Duration.ofSeconds(5).toMillis());
        }
    }

    @Test
    void get_does_not_retry_a_second_time_and_still_fails_if_every_connection_is_closed_without_a_response()
            throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            AtomicInteger connectionCount = new AtomicInteger();
            Thread serverThread = new Thread(() -> {
                try {
                    // Every connection is closed without a response - proves the retry is
                    // bounded (exactly one extra attempt), not an unbounded/looping retry that
                    // would hide a genuinely unreachable backend.
                    for (int i = 0; i < 2; i++) {
                        try (Socket socket = serverSocket.accept()) {
                            connectionCount.incrementAndGet();
                        }
                    }
                } catch (IOException e) {
                    // best effort
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ApiClient apiClient = new ApiClient("http://localhost:" + port + "/", "test-token");
            ApiException ex = assertThrows(ApiException.class, apiClient::getMyLocation);
            assertEquals(true, ex.isCommunicationFailure());

            serverThread.join(Duration.ofSeconds(5).toMillis());
            assertEquals(2, connectionCount.get(),
                    "Exactly one retry (two connection attempts total) should have been made");
        }
    }

    private static void readHttpRequestHeaders(Socket socket) throws IOException {
        var in = socket.getInputStream();
        int state = 0; // progress through matching the "\r\n\r\n" header terminator
        int b;
        while (state < 4 && (b = in.read()) != -1) {
            char c = (char) b;
            if ((state == 0 || state == 2) && c == '\r') {
                state++;
            } else if ((state == 1 || state == 3) && c == '\n') {
                state++;
            } else {
                state = 0;
            }
        }
    }
}
