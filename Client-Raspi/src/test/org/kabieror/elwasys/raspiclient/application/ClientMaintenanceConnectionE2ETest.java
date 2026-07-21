package org.kabieror.elwasys.raspiclient.application;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kabieror.elwasys.common.maintenance.GetLogRequest;
import org.kabieror.elwasys.common.maintenance.GetLogResponse;
import org.kabieror.elwasys.common.maintenance.GetStatusRequest;
import org.kabieror.elwasys.common.maintenance.GetStatusResponse;
import org.kabieror.elwasys.common.maintenance.IClientConnection;
import org.kabieror.elwasys.common.maintenance.MaintenanceResponse;
import org.kabieror.elwasys.common.maintenance.MaintenanceServer;
import org.kabieror.elwasys.common.maintenance.RestartAppRequest;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-component E2E (test plan P21 + P22): the maintenance channel between the
 * portal and a running client. The portal side is represented by the very
 * {@link MaintenanceServer} class the portal wraps in its
 * MaintenanceConnectionManager; the client side is the real client application
 * booted headlessly. They talk over a real TCP socket.
 *
 * The running client registers itself at the server under its location name and
 * then answers the portal's maintenance requests:
 *  - P21 log:     the portal fetches the client's log file (GetLogRequest).
 *  - P22 status:  the portal queries the client's interface status/uptime
 *                 (GetStatusRequest) — the same call that drives the dashboard's
 *                 connection indicator.
 *  - P21 restart: the portal tells the client to restart (RestartAppRequest),
 *                 which the client dispatches to ElwaManager.restart().
 *
 * Run: ./run-cross-component-e2e.sh
 *      (or xvfb-run mvn test -Dtest=ClientMaintenanceConnectionE2ETest)
 * See kb/08-test-plan.md.
 */
@TestMethodOrder(OrderAnnotation.class)
public class ClientMaintenanceConnectionE2ETest {

    private static final int FHEM_PORT = 7083;
    private static final int MAINTENANCE_PORT = 3610;
    private static final String LOCATION = "Default";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static MaintenanceServer server;
    private static FhemSimulator fhem;
    private static Path workDir;
    private static IClientConnection clientConnection;

    @BeforeAll
    static void bootServerAndClient() throws Exception {
        resetLocationRegistration();

        // The "portal" side: a maintenance server listening for the client.
        server = new MaintenanceServer(MAINTENANCE_PORT, 50000);

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-maint-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "database.server=localhost:5432",
                "database.name=elwasys",
                "database.user=elwaclient1",
                "database.password=elwaclient1",
                "database.useSsl=false",
                "backend.url=" + TestBackend.url(),
                "backend.token=" + TestBackend.token(),
                "location=" + LOCATION,
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "instance.port=8282",
                // The client connects its maintenance client to our server.
                "maintenance.server=localhost",
                "maintenance.port=" + MAINTENANCE_PORT,
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-maint-client");
        System.setProperty("user.dir", workDir.toString());

        FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(45));

        // Wait for the running client to register at the maintenance server.
        assertTrue(waitUntil(() -> {
            clientConnection = server.getClientConnection(LOCATION);
            return clientConnection != null;
        }, Duration.ofSeconds(30)), "The running client should register at the maintenance server");
    }

    @AfterAll
    static void shutdown() {
        try {
            FxToolkit.cleanupStages();
        } catch (Exception ignored) {
            // best effort
        }
        if (fhem != null) {
            fhem.stop();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    @Order(1)
    void the_portal_can_fetch_the_clients_log() throws Exception {
        final MaintenanceResponse response = clientConnection.sendQuery(new GetLogRequest());
        assertNotNull(response, "The client should answer the log request");
        assertTrue(response instanceof GetLogResponse,
                "The response should be a GetLogResponse, was " + response.getClass().getSimpleName());
        // The client returns its log file contents (possibly empty list, never null).
        assertNotNull(((GetLogResponse) response).getLogContent(), "The log content must not be null");
    }

    @Test
    @Order(2)
    void the_portal_can_query_the_clients_status() throws Exception {
        final MaintenanceResponse response = clientConnection.sendQuery(new GetStatusRequest());
        assertNotNull(response, "The client should answer the status request");
        assertTrue(response instanceof GetStatusResponse,
                "The response should be a GetStatusResponse, was " + response.getClass().getSimpleName());
        final GetStatusResponse status = (GetStatusResponse) response;
        assertNotNull(status.getInterfaceStatus(), "The interface status should be reported");
        assertNotNull(status.getStartupTime(), "The client should report its startup time");
        assertNotNull(status.getRunningExecutions(), "The running executions list should be present");
    }

    @Test
    @Order(3)
    void the_portal_can_restart_the_client() throws Exception {
        // Observe the restart by listening for the close event (restart=true),
        // which ElwaManager.restart() fires before re-initialising.
        final AtomicBoolean restarted = new AtomicBoolean(false);
        ElwaManager.instance.listenToCloseEvent(restart -> {
            if (restart) {
                restarted.set(true);
            }
        });

        clientConnection.sendCommand(new RestartAppRequest());

        assertTrue(waitUntil(restarted::get, Duration.ofSeconds(15)),
                "The restart command from the portal should trigger a client restart");
    }

    // --- helpers ------------------------------------------------------------

    private static void resetLocationRegistration() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE locations SET client_uid=NULL, client_last_seen=NULL WHERE name='" + LOCATION + "'");
        }
    }

    private static void waitForState(MainFormState target, Duration timeout) throws InterruptedException {
        assertTrue(waitUntil(() -> {
            final var c = ElwaManager.instance.getMainFormController();
            return c != null && c.getMainFormState() == target;
        }, timeout), "Expected to reach state " + target);
    }

    private static boolean waitUntil(BooleanSupplierEx condition, Duration timeout) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return condition.getAsBoolean();
    }

    @FunctionalInterface
    private interface BooleanSupplierEx {
        boolean getAsBoolean();
    }
}
