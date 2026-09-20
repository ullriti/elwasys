package org.kabieror.elwasys.raspiclient.api;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.api.dto.LocationDto;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest für den Befund aus dem bewussten Offline-Test am Terminal Hilarenhaus
 * (2026-09-20, siehe docs/worklog): Bei getrenntem Backend lief JEDER Bedienschritt erneut in
 * das volle Zeitlimit von 10 s, obwohl der Client längst wusste, dass niemand antwortet - die
 * Hintergrundabfrage (alle 20 s) war bis zur ersten Buchung schon rund zehnmal gescheitert.
 * Der Bewohner wartete dadurch zweimal rund 10 s ohne jede Rückmeldung; auf die Millisekunde
 * gemessen 10,008 s (Karte auflegen -> Geräteliste) und 10,002 s (Bestätigen -> Maschine an).
 * <p>
 * Der Test misst BEWUSST Zeit statt nur den Zustandsschalter zu prüfen: Ohne den Fix ist der
 * Zustandsschalter zwar korrekt gesetzt, der zweite Aufruf dauert aber trotzdem die vollen
 * 10 s - genau das, was den Bewohner stört. Eine Prüfung nur auf
 * {@link ApiClient#isBackendUnreachable()} wäre also grün, ohne das Problem zu erfassen.
 * <p>
 * Damit der Test schnell bleibt, wird der erste (teure) Fehlschlag NICHT über eine
 * Zeitüberschreitung erzeugt, sondern über die bereits bekannte "Verbindung ohne Antwort
 * geschlossen"-Fehlerklasse (vgl. {@link ApiClientTransientRetryTest}) - die scheitert nach
 * dem eingebauten einmaligen Wiederholungsversuch sofort. Erst der ZWEITE Aufruf läuft gegen
 * einen stummen Server und darf dann nicht mehr das volle Zeitlimit ausschöpfen.
 */
class ApiClientOfflineFastFailTest {

    /** Grenze zwischen Schnellfehler (2 s) und vollem Zeitlimit (10 s) - großzügig gewählt. */
    private static final Duration FAST_FAIL_UPPER_BOUND = Duration.ofSeconds(6);

    @Test
    void a_known_unreachable_backend_fails_fast_instead_of_waiting_the_full_timeout() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            CountDownLatch silentConnectionReached = new CountDownLatch(1);

            Thread serverThread = new Thread(() -> {
                try {
                    // Aufruf 1 (inkl. des eingebauten einmaligen Wiederholungsversuchs):
                    // annehmen und sofort ohne Antwort schließen -> Kommunikationsfehler,
                    // ohne dass der Test auf eine Zeitüberschreitung warten muss.
                    for (int i = 0; i < 2; i++) {
                        try (Socket ignored = serverSocket.accept()) {
                            // absichtlich keine Antwort
                        }
                    }
                    // Aufruf 2: annehmen und STUMM offen halten -> hier greift das Zeitlimit.
                    Socket silent = serverSocket.accept();
                    silentConnectionReached.countDown();
                    // Offen halten, bis der Test fertig ist; wird mit dem ServerSocket verworfen.
                    Thread.sleep(30_000);
                    silent.close();
                } catch (IOException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ApiClient apiClient = new ApiClient("http://localhost:" + port + "/", "test-token");
            assertFalse(apiClient.isBackendUnreachable(),
                    "Frisch gebauter Client darf das Backend nicht vorab für tot erklären.");

            // Aufruf 1: scheitert schnell und markiert das Backend als nicht erreichbar.
            assertThrows(ApiException.class, apiClient::getMyLocation);
            assertTrue(apiClient.isBackendUnreachable(),
                    "Nach einem Kommunikationsfehler muss das Backend als nicht erreichbar gelten.");

            // Aufruf 2: läuft gegen den stummen Server - und darf jetzt NICHT mehr 10 s dauern.
            long startedAt = System.nanoTime();
            assertThrows(ApiException.class, apiClient::getMyLocation);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertTrue(silentConnectionReached.await(1, TimeUnit.SECONDS),
                    "Der zweite Aufruf hat den stummen Server nie erreicht - der Test misst dann "
                            + "nicht, was er messen soll.");
            assertTrue(elapsed.compareTo(FAST_FAIL_UPPER_BOUND) < 0,
                    "Ein als nicht erreichbar bekanntes Backend muss schnell scheitern, nicht das "
                            + "volle Zeitlimit ausschöpfen. Gemessen: " + elapsed.toMillis() + " ms, "
                            + "erlaubt: unter " + FAST_FAIL_UPPER_BOUND.toMillis() + " ms.");
        }
    }

    @Test
    void a_successful_response_clears_the_unreachable_state() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            // Der Test steuert das Serververhalten ueber EINEN Schalter, statt Verbindungen zu
            // zaehlen: Wie viele Verbindungen der HttpClient fuer Aufruf + eingebauten
            // Wiederholungsversuch tatsaechlich oeffnet (und ob er sie aus seinem Pool
            // wiederverwendet), ist eine Eigenschaft der JDK-Implementierung - ein Test, der
            // darauf baut, prueft das falsche und wird zeitabhaengig.
            java.util.concurrent.atomic.AtomicBoolean answerRequests =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            Thread serverThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try (Socket socket = serverSocket.accept()) {
                        if (!answerRequests.get()) {
                            continue; // ohne Antwort schliessen -> Kommunikationsfehler
                        }
                        readHttpRequestHeaders(socket);
                        String body = "{\"id\":1,\"name\":\"Default\"}";
                        String response = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/json\r\n"
                                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                                + "Connection: close\r\n"
                                + "\r\n"
                                + body;
                        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                        socket.getOutputStream().flush();
                    } catch (IOException e) {
                        return;
                    }
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            ApiClient apiClient = new ApiClient("http://localhost:" + port + "/", "test-token");
            assertThrows(ApiException.class, apiClient::getMyLocation);
            assertTrue(apiClient.isBackendUnreachable(),
                    "Nach einem Kommunikationsfehler muss das Backend als nicht erreichbar gelten.");

            answerRequests.set(true);
            LocationDto location = apiClient.getMyLocation();
            assertFalse(apiClient.isBackendUnreachable(),
                    "Eine erfolgreiche Antwort muss den Offline-Zustand aufheben - sonst bliebe der "
                            + "Client dauerhaft im Schnellfehler-Modus haengen.");
            assertTrue(location != null && location.name() != null);
        }
    }

    private static void readHttpRequestHeaders(Socket socket) throws IOException {
        java.io.InputStream in = socket.getInputStream();
        StringBuilder headers = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            headers.append((char) b);
            int length = headers.length();
            if (length >= 4 && headers.charAt(length - 4) == '\r' && headers.charAt(length - 3) == '\n'
                    && headers.charAt(length - 2) == '\r' && headers.charAt(length - 1) == '\n') {
                return;
            }
        }
    }
}
