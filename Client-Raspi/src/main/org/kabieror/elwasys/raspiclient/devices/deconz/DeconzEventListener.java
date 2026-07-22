package org.kabieror.elwasys.raspiclient.devices.deconz;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.kabieror.elwasys.raspiclient.configuration.WashguardConfiguration;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzChangeType;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzConfig;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzEvent;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeconzEventListener extends TextWebSocketHandler {
    private static final Integer INITIAL_RECONNECT_DELAY_SECONDS = 5;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<IDeconzPowerMeasurementEventListener> powerMeasurementEventListeners = new ArrayList<>();
    private final List<IDeconzDeviceStateEventListener> deviceStateEventListeners = new ArrayList<>();
    private final List<IDeconzDeviceRegisteredListener> deviceRegisteredListeners = new ArrayList<>();
    private Integer reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;
    private final AtomicBoolean isReconnectRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final WebSocketClient client = new StandardWebSocketClient();
    private WebSocketSession webSocketSession;

    private final Gson gson = new Gson();
    private String host;
    private int port;

    public DeconzEventListener(WashguardConfiguration configuration, DeconzApiAdapter apiAdapter) throws IOException, InterruptedException {
        var deconzUri = URI.create(configuration.getDeconzServer());
        this.host = deconzUri.getHost();
        var deconzConfig = apiAdapter.parseResponse(
                apiAdapter.request("config", r -> r.GET()),
                DeconzConfig.class);
        this.port = deconzConfig.websocketport();
    }

    public void listenToPowerMeasurementReceived(IDeconzPowerMeasurementEventListener listener) {
        this.powerMeasurementEventListeners.add(listener);
    }

    public void listenToDeviceStateEvent(IDeconzDeviceStateEventListener listener) {
        this.deviceStateEventListeners.add(listener);
    }

    public void start() {
        openConnection();
    }

    public void stop() {
        this.reconnectScheduler.shutdown();
        if (this.webSocketSession != null && this.webSocketSession.isOpen()) {
            try {
                this.webSocketSession.close();
            } catch (IOException e) {
                this.logger.warn("Failed to close web socket connection");
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        this.logger.info("Connection to deCONZ started");
        super.afterConnectionEstablished(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        this.logger.warn("Transport Error: ", exception);
        super.handleTransportError(session, exception);
        // Ein Transport-Fehler beendet i. d. R. die Verbindung (afterConnectionClosed folgt) -
        // trotzdem hier bereits einen Reconnect anstoßen, damit die Programm-Ende-Erkennung
        // nach einem deCONZ-Neustart/Verbindungsabbruch nicht dauerhaft ausfällt (Issue #19).
        // Doppeltes Planen ist durch isReconnectRunning ausgeschlossen.
        scheduleReconnect();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        this.logger.warn("Connection to deCONZ closed");
        super.afterConnectionClosed(session, status);
        if (session.equals(this.webSocketSession)) {
            this.webSocketSession = null;
        }
        // Anders als der Alt-Code plante bisher NUR ein fehlgeschlagener VerbindungsAUFBAU einen
        // Reconnect - der Abbruch einer bestehenden Verbindung blieb unbehandelt und legte die
        // Leistungsmessung/Steckdosensteuerung bis zum App-Neustart lahm (Issue #19). Jetzt wird
        // wie beim Backend-WS-Client (TerminalWebSocketClient) auch hier neu verbunden. Der
        // Stop-Fall ist abgedeckt: scheduleReconnect() bricht bei heruntergefahrenem Scheduler ab.
        scheduleReconnect();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        byte[] rawBytes = message.getPayload().getBytes(StandardCharsets.UTF_8);
        try {
            DeconzEvent event = gson.fromJson(new String(rawBytes), DeconzEvent.class);

            if (event.r() == DeconzResourceType.sensors
                    && event.state() != null
                    && event.state().power() != null) {
                this.powerMeasurementEventListeners.forEach(
                        l -> l.onPowerMeasurementReceived(event));

            } else if (event.e() == DeconzChangeType.added
                    && event.r() == DeconzResourceType.lights) {
                this.deviceRegisteredListeners.forEach(
                        l -> l.onDeviceRegistered(event.uniqueid()));

            } else if (event.r() == DeconzResourceType.lights
                    && event.state() != null
                    && event.state().on() != null) {
                this.deviceStateEventListeners.forEach(
                        l -> l.onDeviceStateChanged(event.uniqueid(), event.state().on()));

            }
        } catch (JsonSyntaxException e) {
            this.logger.error("Failed to read event data.", e);
        } catch (Exception e) {
            this.logger.error("Unexpected exception while trying to deserialize deCONZ event data.", e);
        }
    }

    private void scheduleReconnect() {
        if (reconnectScheduler.isShutdown()) {
            return;
        }
        if (isReconnectRunning.compareAndSet(false, true)) {
            this.logger.info("Scheduling reconnect in " + reconnectDelaySeconds + " seconds");
            reconnectScheduler.schedule(() -> openConnection(), reconnectDelaySeconds, TimeUnit.SECONDS);
            reconnectDelaySeconds = (int) Math.min(300, Math.round(reconnectDelaySeconds * 1.5));
        }
    }

    private void openConnection() {
        // Nach stop() (Scheduler heruntergefahren) keine Verbindung mehr aufbauen - schließt das
        // Fenster, in dem ein noch vor stop() eingeplanter Reconnect sonst doch verbinden würde
        // (Stop-Flag beachten, analog zum TerminalWebSocketClient, Issue #19).
        if (reconnectScheduler.isShutdown()) {
            return;
        }
        logger.info("Starting web socket connection to deCONZ");
        client.execute(this, String.format("ws://%s:%s", host, port))
                .whenComplete((result, ex) -> {
                    isReconnectRunning.set(false);
                    if (result != null) {
                        this.webSocketSession = result;
                        this.logger.info("Successfully connected");
                        this.reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;
                    } else if (ex != null) {
                        this.logger.error("Failed to connect", ex);
                        scheduleReconnect();
                    }
                });
    }

    public void listenToDeviceRegisteredEvent(IDeconzDeviceRegisteredListener listener) {
        deviceRegisteredListeners.add(listener);
    }
}

