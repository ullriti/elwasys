package org.kabieror.elwasys.raspiclient.application.deconzsimulator;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzAuthenticationSuccessEntity;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzAuthenticationUser;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzChangeType;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzConfig;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzDevice;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzDeviceState;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzEvent;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzEventDeviceState;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A fake deCONZ REST + WebSocket gateway for the Client-Raspi E2E test
 * harness - the deCONZ counterpart to the existing {@code FhemSimulator}
 * (see {@code fhemsimulator/} in this test tree). It implements just enough
 * of the real deCONZ HTTP API (see {@code Client-Raspi/docs/deconz} and
 * {@code devices/deconz/}) to drive the client's real production code -
 * {@code DeconzApiAdapter}, {@code DeconzEventListener},
 * {@code DeconzDevicePowerManager}, {@code DeconzRegistrationService} -
 * through the booking / auto-end / abort flow (test plan C1/C4/C5, C11,
 * C12; see docs/kb/08-test-plan.md), without a real ConBee/deCONZ installation.
 *
 * <h2>Modeled endpoints</h2>
 * <ul>
 *     <li>{@code POST /api} - authentication; always succeeds, returns a
 *     fixed fake API token.</li>
 *     <li>{@code GET /api/{token}/config} - reports the WebSocket port
 *     (read once by {@code DeconzEventListener} on startup).</li>
 *     <li>{@code PUT /api/{token}/config} - pairing/registration
 *     (permitjoin); accepted (200) but otherwise a no-op - device
 *     registration is not exercised by the booking/auto-end/abort flow this
 *     simulator targets (Phase 4 AP1 scope, see docs/kb/05-migration-plan.md).
 *     {@link #sendDeviceAddedEvent(String)} is available for a future test
 *     that does want to exercise {@code DeconzRegistrationService}.</li>
 *     <li>{@code GET /api/{token}/lights/{id}} - current on/off +
 *     reachability of a simulated switch.</li>
 *     <li>{@code PUT /api/{token}/lights/{id}/state} - switches a simulated
 *     light; like the real gateway, the response confirms only that the
 *     command was accepted - the actual state change is reported
 *     asynchronously via a WebSocket "changed" event shortly after, which is
 *     what {@code DeconzService#waitForDeviceState} actually waits for.</li>
 * </ul>
 *
 * <h2>Power measurements</h2>
 * Power measurement events (used by the auto-end detection, test plan C11)
 * are not produced automatically - a test pushes them explicitly via
 * {@link #sendPowerMeasurement(String, double)} as {@code sensors} WebSocket
 * events, using a {@code uniqueid} that starts with the light's id (the
 * production {@code DeconzDevicePowerManager} matches running executions by
 * {@code event.uniqueid().startsWith(device.getDeconzUuid())}, mirroring how
 * a real deCONZ power-metering plug's sensor resource shares its light
 * resource's unique-id prefix).
 *
 * <h2>Pre-registered devices</h2>
 * Four lights are pre-registered, named "wm1".."wm4" - matching
 * {@code FhemSimulator}'s "wm1sw".."wm4sw" naming. The id used in the
 * simulator's REST paths ({@code lights/{id}}) is also the value E2E tests
 * seed into {@code devices.deconz_uuid} for their fixture device.
 */
public class DeconzSimulator {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Gson gson = new Gson();
    private final Map<String, SimulatedLight> lights = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final DeconzWebSocketServer wsServer;
    private final String apiToken = "sim-token";

    private HttpServer httpServer;

    public DeconzSimulator() throws IOException {
        for (int i = 1; i <= 4; i++) {
            this.lights.put("wm" + i, new SimulatedLight("Waschmaschine " + i));
        }
        this.wsServer = new DeconzWebSocketServer();
    }

    /**
     * Starts the simulator: the WebSocket server first (so its port is
     * already being served once the HTTP "config" endpoint can report it),
     * then the REST HTTP server on {@code httpPort}. Returns once both are
     * listening.
     */
    public void start(int httpPort) throws IOException {
        this.wsServer.start();
        this.httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        this.httpServer.createContext("/api", this::handle);
        this.httpServer.setExecutor(Executors.newCachedThreadPool());
        this.httpServer.start();
    }

    public void stop() {
        if (this.httpServer != null) {
            this.httpServer.stop(0);
        }
        this.wsServer.stop();
        this.scheduler.shutdownNow();
    }

    /**
     * Pushes a "sensors" power measurement WebSocket event for the light
     * with the given id, as a real deCONZ power-metering plug would.
     *
     * @param lightUuid the id of a pre-registered (or previously added)
     *                  light, e.g. "wm1"
     * @param watts     the simulated current power draw in watts
     */
    public void sendPowerMeasurement(String lightUuid, double watts) {
        final String sensorUuid = lightUuid + "-power";
        final var event = new DeconzEvent(DeconzChangeType.changed, 0, DeconzResourceType.sensors,
                new DeconzEventDeviceState(null, null, watts, null), null, sensorUuid);
        this.wsServer.broadcastText(this.gson.toJson(event));
        this.logger.info("Simulated deCONZ power measurement for {}: {}W", lightUuid, watts);
    }

    /**
     * Pushes a light "added" WebSocket event, as a real ConBee stick would
     * when a new device joins the Zigbee network during a registration
     * scan. Not used by the booking/auto-end/abort E2E scenarios (Phase 4
     * AP1 scope); available for a future test of
     * {@code DeconzRegistrationService}.
     */
    public void sendDeviceAddedEvent(String uuid) {
        this.lights.putIfAbsent(uuid, new SimulatedLight("Neues Gerät"));
        final var event = new DeconzEvent(DeconzChangeType.added, 0, DeconzResourceType.lights,
                null, null, uuid);
        this.wsServer.broadcastText(this.gson.toJson(event));
    }

    /** For test assertions: whether the simulator currently thinks a light is switched on. */
    public boolean isOn(String uuid) {
        final SimulatedLight light = this.lights.get(uuid);
        return light != null && light.on;
    }

    // ------------------------------------------------------------- HTTP API

    private void handle(HttpExchange exchange) throws IOException {
        try {
            final String[] segments = exchange.getRequestURI().getPath().split("/");
            final String method = exchange.getRequestMethod();

            if (segments.length == 2 && "api".equals(segments[1]) && "POST".equals(method)) {
                handleAuthenticate(exchange);
            } else if (segments.length >= 4 && "config".equals(segments[3])) {
                if ("GET".equals(method)) {
                    handleGetConfig(exchange);
                } else if ("PUT".equals(method)) {
                    handlePutConfig(exchange);
                } else {
                    sendJson(exchange, 405, "[]");
                }
            } else if (segments.length == 5 && "lights".equals(segments[3]) && "GET".equals(method)) {
                handleGetLight(exchange, segments[4]);
            } else if (segments.length == 6 && "lights".equals(segments[3]) && "state".equals(segments[5])
                    && "PUT".equals(method)) {
                handlePutLightState(exchange, segments[4]);
            } else {
                sendJson(exchange, 404, "[]");
            }
        } catch (final Exception e) {
            this.logger.error("Error handling deCONZ simulator request " + exchange.getRequestURI(), e);
            sendJson(exchange, 500, "[]");
        } finally {
            exchange.close();
        }
    }

    private void handleAuthenticate(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes(); // drain, credentials are not actually checked
        final var success = new DeconzAuthenticationSuccessEntity(new DeconzAuthenticationUser(this.apiToken));
        sendJson(exchange, 200, this.gson.toJson(new DeconzAuthenticationSuccessEntity[]{success}));
    }

    private void handleGetConfig(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, this.gson.toJson(new DeconzConfig(this.wsServer.getPort())));
    }

    private void handlePutConfig(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes(); // drain (permitjoin pairing request)
        sendJson(exchange, 200, "[{\"success\":{\"/config/permitjoin\":true}}]");
    }

    private void handleGetLight(HttpExchange exchange, String id) throws IOException {
        final SimulatedLight light = this.lights.get(id);
        if (light == null) {
            sendJson(exchange, 404, "[]");
            return;
        }
        final var device = new DeconzDevice(light.name, new DeconzDeviceState(light.on, true), id);
        sendJson(exchange, 200, this.gson.toJson(device));
    }

    private void handlePutLightState(HttpExchange exchange, String id) throws IOException {
        final byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
        final SimulatedLight light = this.lights.get(id);
        if (light == null) {
            sendJson(exchange, 404, "[]");
            return;
        }
        final String body = new String(bodyBytes, StandardCharsets.UTF_8);
        final var requested = this.gson.fromJson(body, DeconzDeviceState.class);
        final boolean newOn = requested != null && requested.on();

        // Confirm the command was accepted immediately (as the real gateway
        // does), then report the actual state change asynchronously via the
        // WebSocket event stream a short moment later - this is what
        // DeconzService#waitForDeviceState is actually waiting on.
        sendJson(exchange, 200, "[{\"success\":{\"/lights/" + id + "/state/on\":" + newOn + "}}]");
        this.scheduler.schedule(() -> {
            light.on = newOn;
            final var event = new DeconzEvent(DeconzChangeType.changed, 0, DeconzResourceType.lights,
                    new DeconzEventDeviceState(newOn, null, null, null), null, id);
            this.wsServer.broadcastText(this.gson.toJson(event));
            this.logger.info("Simulated deCONZ light {} switched {}", id, newOn ? "on" : "off");
        }, 50, TimeUnit.MILLISECONDS);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        final byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
