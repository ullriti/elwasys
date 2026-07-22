package org.kabieror.elwasys.raspiclient.application;

import javafx.scene.Node;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;

import javafx.scene.input.KeyCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client E2E (test plan C12): after starting a program on a device, the user
 * can abort the running execution. The terminal asks for confirmation and,
 * once confirmed, stops the execution and frees the device.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientAbortExecutionE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientAbortExecutionE2ETest {

    private static final int FHEM_PORT = 7077;
    private static final String DEVICE_NAME = "E2E-WM-Abort";
    private static final String PROGRAM_NAME = "E2E-Waschen-Abort";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    private static String cardId;
    private static int deviceId;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-abort-e2e");
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
                "instance.port=8276",
                "maintenance.server=localhost",
                "maintenance.port=3596",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-abort-client");
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
    void a_running_execution_can_be_aborted() throws Exception {
        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();

        // Log in and start a program on the device.
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);
        assertTrue(waitUntil(() -> controller.getRegisteredUser() != null, Duration.ofSeconds(10)),
                "The card holder should be logged in");

        assertTrue(waitUntil(() -> isEnabled(buttonFor(DEVICE_NAME, ".select-button")), Duration.ofSeconds(10)),
                "The 'book device' button should be enabled");
        robot.clickOn(buttonFor(DEVICE_NAME, ".select-button"));
        waitForState(MainFormState.CONFIRMATION, Duration.ofSeconds(10));

        clickById("#forwardButton"); // Start
        assertTrue(waitUntil(ClientAbortExecutionE2ETest::hasRunningExecution, Duration.ofSeconds(15)),
                "The execution should be running before we abort it");

        // The device tile now offers an abort button; use it and confirm.
        assertTrue(waitUntil(() -> isVisible(buttonFor(DEVICE_NAME, ".abort-button")), Duration.ofSeconds(10)),
                "The abort button should be shown while the device is occupied");
        robot.clickOn(buttonFor(DEVICE_NAME, ".abort-button"));
        waitForState(MainFormState.CONFIRM_PROGRAM_ABORTION, Duration.ofSeconds(10));

        clickById("#forwardButton"); // Bestätigen

        assertTrue(waitUntil(() -> !hasRunningExecution(), Duration.ofSeconds(15)),
                "After confirming, the execution should no longer be running");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "3" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN " +
                    "(SELECT id FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%'))");
            s.executeUpdate("DELETE FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-%'");
            s.executeUpdate("DELETE FROM programs WHERE name LIKE 'E2E-%'");

            final int programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'FIXED', 3600, 0, 1.50, NULL, NULL, TRUE, 0, TRUE) RETURNING id");
            deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                            "fhem_power_name, deconz_uuid, auto_end_power_threshold, auto_end_wait_time, enabled) " +
                            "VALUES ('" + DEVICE_NAME + "', 1, " + locationId +
                            ", 'wm1', 'wm1sw', 'wm1pw', '', 0.5, 20, TRUE) RETURNING id");
            s.executeUpdate("INSERT INTO device_program_rel (device_id, program_id) VALUES ("
                    + deviceId + ", " + programId + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES ("
                    + programId + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                    + deviceId + ", " + groupId + ")");

            final int userId = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Abbruch', 'e2e_abort_" + System.currentTimeMillis() + "', '" + cardId + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");
        }
    }

    private static boolean hasRunningExecution() {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(
                     "SELECT COUNT(*) FROM executions WHERE finished=false AND device_id=" + deviceId)) {
            r.next();
            return r.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** The button node with the given style class inside the named device tile. */
    private static Node buttonFor(String deviceName, String styleClass) {
        for (Node tile : primaryStage.getScene().getRoot().lookupAll(".device-list-item")) {
            if (containsText(tile, deviceName)) {
                return tile.lookup(styleClass);
            }
        }
        return null;
    }

    private static boolean isEnabled(Node n) {
        return n != null && !n.isDisabled();
    }

    private static boolean isVisible(Node n) {
        return n != null && n.isVisible();
    }

    private static boolean containsText(Node node, String text) {
        if (node instanceof javafx.scene.control.Labeled labeled && text.equals(labeled.getText())) {
            return true;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void clickById(String selector) {
        final Node node = robot.lookup(selector).query();
        robot.clickOn(node);
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
