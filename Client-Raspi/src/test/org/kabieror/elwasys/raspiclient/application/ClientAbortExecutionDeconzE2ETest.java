package org.kabieror.elwasys.raspiclient.application;

import javafx.scene.Node;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.application.deconzsimulator.DeconzSimulator;
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
 * deCONZ counterpart of {@link ClientAbortExecutionE2ETest} (test plan C12):
 * after starting a program on a device, the user can abort the running
 * execution - here with the client talking to the simulated deCONZ gateway
 * instead of fhem (both the initial "switch on" and the abort's "switch
 * off" go through {@code DeconzDevicePowerManager}).
 *
 * Run: xvfb-run mvn test -Dtest=ClientAbortExecutionDeconzE2ETest
 * See docs/kb/06-ui-tests.md, docs/kb/08-test-plan.md.
 */
public class ClientAbortExecutionDeconzE2ETest {

    private static final int DECONZ_PORT = 7092;
    private static final String DEVICE_NAME = "E2E-Deconz-WM-Abort";
    private static final String PROGRAM_NAME = "E2E-Deconz-Waschen-Abort";
    private static final String DECONZ_UUID = "wm3";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static DeconzSimulator deconz;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    private static String cardId;
    private static int deviceId;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        deconz = new DeconzSimulator();
        deconz.start(DECONZ_PORT);

        workDir = Files.createTempDirectory("elwasys-abort-deconz-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "backend.url=" + TestBackend.url(),
                "backend.token=" + TestBackend.token(),
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "deconz.server=http://localhost:" + DECONZ_PORT,
                "deconz.user=sim",
                "deconz.password=sim",
                "instance.port=8292",
                "maintenance.server=localhost",
                "maintenance.port=3692",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-abort-deconz-client");
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
        if (deconz != null) {
            deconz.stop();
        }
    }

    @Test
    void a_running_execution_can_be_aborted_via_deconz() throws Exception {
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
        assertTrue(waitUntil(ClientAbortExecutionDeconzE2ETest::hasRunningExecution, Duration.ofSeconds(15)),
                "The execution should be running before we abort it");
        assertTrue(waitUntil(() -> deconz.isOn(DECONZ_UUID), Duration.ofSeconds(5)),
                "The simulated deCONZ light should have been switched on");

        // The device tile now offers an abort button; use it and confirm.
        assertTrue(waitUntil(() -> isVisible(buttonFor(DEVICE_NAME, ".abort-button")), Duration.ofSeconds(10)),
                "The abort button should be shown while the device is occupied");
        robot.clickOn(buttonFor(DEVICE_NAME, ".abort-button"));
        waitForState(MainFormState.CONFIRM_PROGRAM_ABORTION, Duration.ofSeconds(10));

        clickById("#forwardButton"); // Bestätigen

        assertTrue(waitUntil(() -> !hasRunningExecution(), Duration.ofSeconds(15)),
                "After confirming, the execution should no longer be running");
        assertTrue(waitUntil(() -> !deconz.isOn(DECONZ_UUID), Duration.ofSeconds(5)),
                "The simulated deCONZ light should have been switched off again by the abort");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "03" + String.format("%07d", System.currentTimeMillis() % 10_000_000L);
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN " +
                    "(SELECT id FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-Deconz-%'))");
            s.executeUpdate("DELETE FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-Deconz-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-Deconz-%'");
            s.executeUpdate("DELETE FROM programs WHERE name LIKE 'E2E-Deconz-%'");

            final int programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'FIXED', 3600, 0, 1.50, NULL, NULL, TRUE, 0, TRUE) RETURNING id");
            deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                            "fhem_power_name, deconz_uuid, auto_end_power_threshold, auto_end_wait_time, enabled) " +
                            "VALUES ('" + DEVICE_NAME + "', 1, " + locationId +
                            ", '', '', '', '" + DECONZ_UUID + "', 0.5, 20, TRUE) RETURNING id");
            s.executeUpdate("INSERT INTO device_program_rel (device_id, program_id) VALUES ("
                    + deviceId + ", " + programId + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES ("
                    + programId + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                    + deviceId + ", " + groupId + ")");

            final int userId = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Deconz Abbruch', 'e2e_deconz_abort_" + System.currentTimeMillis() + "', '"
                            + cardId + "', " + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            // Phase 4 CI-Stabilität (deCONZ Test-Isolation, siehe docs/kb/05 Änderungslog): entferne
            // unfertige Ausführungen auf Geräten OHNE deCONZ-Id am Standort, bevor dieses
            // deCONZ-Terminal startet. Der Start-Wiederaufnahme-Scan von ElwaManager#initiate()
            // ist standortweit; eine von einer FRÜHEREN, fhem-basierten Testklasse hinterlassene
            // unfertige Ausführung (z. B. ClientUsageE2ETest auf "E2E-Waschmaschine") würde sonst
            // über den deCONZ-Gateway eingeschaltet und scheitern (keine deCONZ-Id → Init-Absturz,
            // nie SELECT_DEVICE). In CI ist die Surefire-Klassenreihenfolge dateisystemabhängig,
            // daher trat das nur dort und reihenfolgeabhängig auf. Diese deCONZ-Tests nehmen beim
            // Start selbst nichts wieder auf, das Löschen ist also gefahrlos.
            s.executeUpdate("DELETE FROM executions WHERE finished=false AND device_id IN "
                    + "(SELECT id FROM devices WHERE location_id=" + locationId
                    + " AND (deconz_uuid IS NULL OR deconz_uuid=''))");
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
