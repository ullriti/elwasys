package org.kabieror.elwasys.raspiclient.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest zu Issue #53: Eine als 2xx (Erfolg) empfangene, aber inhaltlich unlesbare
 * Antwort (kaputtes JSON) wurde früher als {@link ApiException} mit {@code httpStatus=0} und
 * damit als Kommunikationsfehler klassifiziert - was fälschlich den Offline-Pfad auslöste
 * (z. B. eine Offline-Buchung, obwohl der Server erreichbar war und evtl. bereits gehandelt
 * hat). Jetzt gilt so eine Antwort als echter Serverfehler und NICHT als Offline-Auslöser.
 * <p>
 * Deterministisch über einen minimalen TCP-Server, der auf die (idempotente)
 * {@code GET .../locations/me}-Anfrage mit {@code 200 OK} und einem kaputten JSON-Body
 * antwortet.
 */
class ApiClientMalformedResponseTest {

    @Test
    void malformed_2xx_body_is_not_treated_as_a_communication_failure() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            Thread serverThread = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    readHttpRequestHeaders(socket);
                    String body = "{ this is not valid json";
                    String response = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/json\r\n"
                            + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                            + "Connection: close\r\n"
                            + "\r\n"
                            + body;
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                } catch (IOException e) {
                    // Best effort; ein fehlender Response schlägt unten als Assertion fehl.
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ApiClient apiClient = new ApiClient("http://localhost:" + port + "/", "test-token");
            ApiException ex = assertThrows(ApiException.class, apiClient::getMyLocation);

            assertFalse(ex.isCommunicationFailure(),
                    "Eine kaputte 2xx-Antwort darf NICHT als Kommunikationsfehler (Offline-Auslöser) gelten");
            assertTrue(ex.isMalformedResponse(),
                    "Eine kaputte 2xx-Antwort soll als malformedResponse gekennzeichnet sein");

            serverThread.join(Duration.ofSeconds(5).toMillis());
        }
    }

    private static void readHttpRequestHeaders(Socket socket) throws IOException {
        var in = socket.getInputStream();
        int state = 0; // Fortschritt beim Erkennen des "\r\n\r\n"-Headerendes
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
