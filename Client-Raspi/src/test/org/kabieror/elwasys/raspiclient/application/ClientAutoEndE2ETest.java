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
 * Client E2E (test plan C11): once a program is running, the terminal detects
 * the end of the program from the device's power consumption. The device draws
 * (almost) no power, so after the configured wait time the execution ends
 * automatically and the device becomes free again.
 *
 * The device is seeded with a very short auto-end wait time to keep the test
 * fast.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientAutoEndE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientAutoEndE2ETest {

    private static final int FHEM_PORT = 7078;
    private static final String DEVICE_NAME = "E2E-WM-AutoEnd";
    private static final String PROGRAM_NAME = "E2E-Waschen-AutoEnd";
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

        workDir = Files.createTempDirectory("elwasys-autoend-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "database.server=localhost:5432",
                "database.name=elwasys",
                "database.user=elwaclient1",
                "database.password=elwaclient1",
                "database.useSsl=false",
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "instance.port=8277",
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3597",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-autoend-client");
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
    void execution_ends_automatically_when_no_power_is_drawn() throws Exception {
        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();

        // Log in and start a program on the device.
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);
        assertTrue(waitUntil(() -> controller.getRegisteredUser() != null, Duration.ofSeconds(10)),
                "The card holder should be logged in");

        assertTrue(waitUntil(() -> isEnabled(selectButtonFor(DEVICE_NAME)), Duration.ofSeconds(10)),
                "The 'book device' button should be enabled");
        robot.clickOn(selectButtonFor(DEVICE_NAME));
        waitForState(MainFormState.CONFIRMATION, Duration.ofSeconds(10));

        clickById("#forwardButton"); // Start
        assertTrue(waitUntil(ClientAutoEndE2ETest::hasRunningExecution, Duration.ofSeconds(15)),
                "The execution should be running first");

        // The device draws no power, so the program auto-ends after the (short)
        // wait time.
        assertTrue(waitUntil(() -> !hasRunningExecution(), Duration.ofSeconds(40)),
                "The execution should end automatically once the device stops drawing power");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "2" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);
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
            // Very short auto-end wait time so the test finishes quickly.
            deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                            "fhem_power_name, deconz_uuid, auto_end_power_threashold, auto_end_wait_time, enabled) " +
                            "VALUES ('" + DEVICE_NAME + "', 1, " + locationId +
                            ", 'wm1', 'wm1sw', 'wm1pw', '', 0.5, 2, TRUE) RETURNING id");
            s.executeUpdate("INSERT INTO device_program_rel (device_id, program_id) VALUES ("
                    + deviceId + ", " + programId + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES ("
                    + programId + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                    + deviceId + ", " + groupId + ")");

            final int userId = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E AutoEnd', 'e2e_autoend_" + System.currentTimeMillis() + "', '" + cardId + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            s.executeUpdate("UPDATE locations SET client_uid=NULL, client_last_seen=NULL WHERE id=" + locationId);
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

    private static Node selectButtonFor(String deviceName) {
        for (Node tile : primaryStage.getScene().getRoot().lookupAll(".device-list-item")) {
            if (containsText(tile, deviceName)) {
                return tile.lookup(".select-button");
            }
        }
        return null;
    }

    private static boolean isEnabled(Node n) {
        return n != null && !n.isDisabled();
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
