package org.kabieror.elwasys.raspiclient.application;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client E2E (test plan C16): the terminal only shows devices that belong to
 * its own location. A device registered at a different location must not appear
 * in the device list.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientDeviceVisibilityE2ETest)
 * See docs/kb/08-test-plan.md.
 */
public class ClientDeviceVisibilityE2ETest {

    private static final int FHEM_PORT = 7079;
    private static final String LOCAL_DEVICE = "E2E-WM-Local";
    private static final String FOREIGN_DEVICE = "E2E-WM-Foreign";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-visibility-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "backend.url=" + TestBackend.url(),
                "backend.token=" + TestBackend.token(),
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "instance.port=8278",
                "maintenance.server=localhost",
                "maintenance.port=3598",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-visibility-client");
        System.setProperty("user.dir", workDir.toString());

        primaryStage = FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
        robot = new FxRobot();
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(45));
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
    }

    @Test
    void only_devices_of_the_own_location_are_shown() throws InterruptedException {
        // Wait until the local device has been rendered (device loading done).
        assertTrue(waitUntil(() -> sceneContainsText(LOCAL_DEVICE), Duration.ofSeconds(15)),
                "The device at this client's location should be listed");

        // The device from a different location must not be shown.
        assertFalse(sceneContainsText(FOREIGN_DEVICE),
                "A device at a different location must not appear in the list");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement()) {
            final int defaultLocation = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN " +
                    "(SELECT id FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%'))");
            s.executeUpdate("DELETE FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-%'");
            s.executeUpdate("DELETE FROM locations WHERE name LIKE 'E2E-Loc-%'");

            // A separate location for the foreign device.
            final int foreignLocation = insertReturningId(s,
                    "INSERT INTO locations (name) VALUES ('E2E-Loc-" + System.currentTimeMillis() + "') RETURNING id");

            insertDevice(s, LOCAL_DEVICE, defaultLocation, groupId);
            insertDevice(s, FOREIGN_DEVICE, foreignLocation, groupId);
        }
    }

    private static void insertDevice(Statement s, String name, int locationId, int groupId) throws Exception {
        final int deviceId = insertReturningId(s,
                "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                        "fhem_power_name, deconz_uuid, auto_end_power_threshold, auto_end_wait_time, enabled) " +
                        "VALUES ('" + name + "', 1, " + locationId +
                        ", 'wm1', 'wm1sw', 'wm1pw', '', 0.5, 20, TRUE) RETURNING id");
        s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                + deviceId + ", " + groupId + ")");
    }

    private static boolean sceneContainsText(String text) {
        final AtomicBoolean found = new AtomicBoolean(false);
        searchText(primaryStage.getScene().getRoot(), text, found);
        return found.get();
    }

    private static void searchText(Node node, String text, AtomicBoolean found) {
        if (found.get()) {
            return;
        }
        if (node instanceof Labeled labeled && text.equals(labeled.getText())) {
            found.set(true);
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                searchText(child, text, found);
            }
        }
    }

    private static int queryInt(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static int insertReturningId(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
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
