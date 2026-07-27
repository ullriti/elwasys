package org.kabieror.elwasys.raspiclient.devices.deconz;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.application.deconzsimulator.DeconzSimulator;
import org.kabieror.elwasys.raspiclient.configuration.WashguardConfiguration;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest zu Issue #19 (nachgefordert als T1 der finalen Review, Issue #87): Bricht die
 * deCONZ-WebSocket-Verbindung ab, muss der {@link DeconzEventListener} sich selbständig neu
 * verbinden und danach wieder Ereignisse empfangen. Ohne das fällt die
 * <em>Programm-Ende-Erkennung</em> - die Kernfunktion des Terminals - bis zum Anwendungsneustart
 * still aus, denn sie hängt allein an den Leistungsmessungen aus diesem Ereignisstrom.
 * <p>
 * Bewusst ein schlanker Test gegen den {@link DeconzSimulator} statt einer weiteren
 * Voll-E2E-Variante: geprüft wird die Verbindungs-Selbstheilung des Listeners, nicht die
 * Terminal-UI. Der Simulator kappt die Verbindung serverseitig
 * ({@code dropWebSocketConnections()}), behält aber seinen Port - genau das Bild eines
 * neugestarteten deCONZ-Gateways.
 */
class DeconzEventListenerReconnectTest {

    /**
     * Der Reconnect ist mit 5 s Backoff geplant (siehe {@code INITIAL_RECONNECT_DELAY_SECONDS});
     * das Zeitfenster ist großzügig, damit ein langsamer CI-Läufer nicht rot wird. Gewartet wird
     * auf die BEDINGUNG (Ereignis empfangen), nicht auf eine feste Dauer.
     */
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(60);

    private static final String LIGHT_UUID = "wm1";

    private DeconzSimulator deconz;

    private int httpPort;

    private DeconzEventListener listener;

    @BeforeEach
    void startSimulator() throws Exception {
        this.deconz = new DeconzSimulator();
        this.httpPort = freePort();
        this.deconz.start(this.httpPort);
    }

    @AfterEach
    void shutdown() {
        if (this.listener != null) {
            this.listener.stop();
        }
        if (this.deconz != null) {
            this.deconz.stop();
        }
    }

    @Test
    void listener_reconnects_after_the_gateway_dropped_the_connection() throws Exception {
        final AtomicInteger receivedMeasurements = new AtomicInteger();
        this.listener = newListener();
        this.listener.listenToPowerMeasurementReceived(event -> receivedMeasurements.incrementAndGet());
        this.listener.start();

        assertTrue(awaitMeasurement(receivedMeasurements, Duration.ofSeconds(30)),
                "Der Listener sollte nach dem Start Leistungsmessungen empfangen");
        assertEquals(1, this.deconz.getAcceptedWebSocketConnections(),
                "Zum Start sollte genau eine WebSocket-Verbindung aufgebaut worden sein");

        // Der Gateway "startet neu": bestehende Verbindung weg, Port bleibt.
        this.deconz.dropWebSocketConnections();

        assertTrue(awaitMeasurement(receivedMeasurements, RECONNECT_TIMEOUT),
                "Nach dem Verbindungsabbruch sollte der Listener neu verbinden und wieder "
                        + "Leistungsmessungen empfangen");

        // Kante zum CAS-Guard isReconnectRunning: handleTransportError UND afterConnectionClosed
        // stoßen beim Abbruch beide scheduleReconnect() an. Beide Aufrufe passieren im selben
        // Moment (Millisekunden nach dem Kappen) und würden ohne den Guard zwei Verbindungen im
        // selben 5-Sekunden-Fenster öffnen - also längst, bevor die erste Messung nach dem
        // Reconnect oben ankommt. Genau zwei Handshakes belegen daher: kein Doppel-Reconnect.
        assertEquals(2, this.deconz.getAcceptedWebSocketConnections(),
                "Ein Abbruch darf genau einen Reconnect auslösen, keine zwei parallelen");
    }

    private DeconzEventListener newListener() throws Exception {
        final WashguardConfiguration configuration = new SimulatorConfiguration(
                "http://localhost:" + this.httpPort);
        return new DeconzEventListener(configuration, new DeconzApiAdapter(configuration));
    }

    /**
     * Wartet darauf, dass (mindestens) eine weitere Leistungsmessung ankommt. Die Messung wird
     * dabei wiederholt gesendet, weil ein Broadcast ins Leere geht, solange (noch) kein Client
     * verbunden ist - der Test darf nicht davon abhängen, den Reconnect-Zeitpunkt zu erraten.
     */
    private boolean awaitMeasurement(AtomicInteger counter, Duration timeout) throws InterruptedException {
        final int before = counter.get();
        final Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            this.deconz.sendPowerMeasurement(LIGHT_UUID, 42.0);
            for (int i = 0; i < 5; i++) {
                if (counter.get() > before) {
                    return true;
                }
                Thread.sleep(100);
            }
        }
        return counter.get() > before;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Konfiguration ohne Datei/Arbeitsverzeichnis: {@code WashguardConfiguration} löst den
     * Dateipfad statisch beim Klassenladen auf, was ein Test nicht zuverlässig steuern kann. Für
     * diesen Test zählen nur die drei deCONZ-Werte.
     */
    private static final class SimulatorConfiguration extends WashguardConfiguration {

        private final String server;

        private SimulatorConfiguration(String server) throws Exception {
            this.server = server;
        }

        @Override
        public String getDeconzServer() {
            return this.server;
        }

        @Override
        public String getDeconzUser() {
            return "sim";
        }

        @Override
        public String getDeconzPassword() {
            return "sim";
        }
    }
}
