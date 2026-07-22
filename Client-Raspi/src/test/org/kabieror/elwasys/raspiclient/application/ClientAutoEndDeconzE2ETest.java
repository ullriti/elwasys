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
 * deCONZ counterpart of {@link ClientAutoEndE2ETest} (test plan C11): once a
 * program is running, the terminal detects the end of the program from the
 * device's power consumption and ends the execution automatically.
 * <p>
 * Unlike the fhem version - which relies solely on the built-in "no power
 * measured yet" fallback ({@code ExecutionManager#startExecution} schedules
 * a provisional auto-stop with a synthetic 0W reading right after starting)
 * - this test additionally exercises a full, genuine power-measurement round
 * trip over the simulated deCONZ WebSocket event stream:
 * <ol>
 *     <li>right after the execution starts, a power reading <em>above</em>
 *     the device's threshold is pushed, which must cancel the provisional
 *     auto-stop (verified by asserting the execution is still running after
 *     the wait time that the provisional stop would have used);</li>
 *     <li>a power reading <em>below</em> the threshold is then pushed,
 *     which must (re-)schedule the auto-stop, and the execution is expected
 *     to actually end once that wait time elapses.</li>
 * </ol>
 * This proves the {@code DeconzEventListener -> DeconzDevicePowerManager ->
 * ExecutionManager#onPowerMeasurementAvailable} pipeline works end-to-end
 * for real "sensors" events, not just the zero-power startup fallback that
 * the plain "device draws no power" scenario alone would already satisfy.
 *
 * Run: xvfb-run mvn test -Dtest=ClientAutoEndDeconzE2ETest
 * See kb/06-ui-tests.md, kb/08-test-plan.md.
 */
public class ClientAutoEndDeconzE2ETest {

    private static final int DECONZ_PORT = 7091;
    private static final String DEVICE_NAME = "E2E-Deconz-WM-AutoEnd";
    private static final String PROGRAM_NAME = "E2E-Deconz-Waschen-AutoEnd";
    private static final String DECONZ_UUID = "wm2";
    private static final double POWER_THRESHOLD = 0.5;
    /** Device's configured auto-end wait time (seconds); kept short for the test. */
    private static final int AUTO_END_WAIT_SECONDS = 4;
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

        workDir = Files.createTempDirectory("elwasys-autoend-deconz-e2e");
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
                "instance.port=8291",
                "maintenance.server=localhost",
                "maintenance.port=3691",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-autoend-deconz-client");
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
    void execution_ends_automatically_after_a_low_power_measurement_via_deconz() throws Exception {
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
        assertTrue(waitUntil(ClientAutoEndDeconzE2ETest::hasRunningExecution, Duration.ofSeconds(15)),
                "The execution should be running first (deCONZ light switched on)");
        assertTrue(waitUntil(() -> deconz.isOn(DECONZ_UUID), Duration.ofSeconds(5)),
                "The simulated deCONZ light should have been switched on");

        // A genuine "device is drawing power" reading, well above the
        // threshold, must cancel the provisional zero-power auto-stop that
        // ExecutionManager#startExecution schedules right after starting.
        deconz.sendPowerMeasurement(DECONZ_UUID, 5.0);

        // Give the provisional auto-stop's wait time a chance to have fired
        // if (and only if) it had NOT been cancelled - the execution must
        // still be running at this point.
        Thread.sleep(Duration.ofSeconds(AUTO_END_WAIT_SECONDS + 1).toMillis());
        assertTrue(hasRunningExecution(),
                "The execution must still be running: the above-threshold power measurement "
                        + "should have cancelled the provisional auto-stop");

        // Now simulate the washing machine finishing: power drops below the
        // threshold, which (re-)schedules the auto-stop.
        deconz.sendPowerMeasurement(DECONZ_UUID, 0.1);

        assertTrue(waitUntil(() -> !hasRunningExecution(), Duration.ofSeconds(AUTO_END_WAIT_SECONDS + 15)),
                "The execution should end automatically once a below-threshold power "
                        + "measurement is received via the deCONZ WebSocket event stream");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "02" + String.format("%07d", System.currentTimeMillis() % 10_000_000L);
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
                            ", '', '', '', '" + DECONZ_UUID + "', " + POWER_THRESHOLD + ", " +
                            AUTO_END_WAIT_SECONDS + ", TRUE) RETURNING id");
            s.executeUpdate("INSERT INTO device_program_rel (device_id, program_id) VALUES ("
                    + deviceId + ", " + programId + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES ("
                    + programId + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                    + deviceId + ", " + groupId + ")");

            final int userId = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Deconz AutoEnd', 'e2e_deconz_autoend_" + System.currentTimeMillis() + "', '"
                            + cardId + "', " + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            // Phase 4 CI-Stabilität (deCONZ Test-Isolation, siehe kb/05 Änderungslog): entferne
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
