package org.kabieror.elwasys.raspiclient.application;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.small.MainFormController;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the 320x240 small terminal UI ({@code ui/small}, see
 * {@code MainForm.fxml}/{@code MainFormController} in that package) - so
 * far the E2E suite only covered {@code ui/medium} (800x480). Per Phase 4
 * AP1 (kb/05-migration-plan.md), this UI stays in use and needs at least
 * smoke coverage before any further modernisation touches it.
 * <p>
 * {@link Main#applicationInterfaceType} decides which FXML/UI size loads
 * (auto-detected from the primary screen width, or forced by the
 * {@code -xsDisplay}/{@code -mdDisplay} command line switches - see
 * {@code Main#start}); this test sets the field directly before boot so the
 * choice does not depend on the headless Xvfb screen's actual resolution.
 * <p>
 * <b>Finding (documented, not a bug - see kb/05-migration-plan.md "Phase 4
 * AP1"):</b> unlike {@code ui/medium}, the small UI's flow is reversed: the
 * user taps a device tile <em>first</em> (gated only by
 * {@code Device#isEnabled}, not by a logged-in user - {@code
 * MainFormController#onDeviceSelected} has no such check), and only then is
 * asked to scan a card ({@code CONFIRMATION_WAIT_FOR_CARD}). Card scans
 * outside of that confirmation flow are ignored ({@code onCardDetected}
 * only reacts while on a confirmation sub-state). This test therefore
 * exercises "select device" before "card login", matching the UI's actual
 * behaviour.
 *
 * Run: xvfb-run mvn test -Dtest=ClientSmallUiSmokeE2ETest
 * See kb/06-ui-tests.md.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClientSmallUiSmokeE2ETest {

    private static final int FHEM_PORT = 7093;
    private static final String DEVICE_NAME = "E2E-Small-WM";
    private static final String PROGRAM_NAME = "E2E-Small-Waschen";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    private static String cardId;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-smalui-e2e");
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
                "instance.port=8293",
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3693",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-smallui-client");
        System.setProperty("user.dir", workDir.toString());

        // Force the small (320x240) UI regardless of the headless Xvfb
        // screen's actual resolution - see Main#start.
        Main.applicationInterfaceType = ApplicationInterfaceType.TOUCH_SMALL;

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
    @Order(1)
    void application_starts_with_the_small_ui_and_reaches_device_selection() {
        assertEquals(320, primaryStage.getScene().getWidth(), 0.1,
                "The small UI's scene should be 320 pixels wide (see Main#start)");
        assertEquals(240, primaryStage.getScene().getHeight(), 0.1,
                "The small UI's scene should be 240 pixels high (see Main#start)");

        final Node devicePane = primaryStage.getScene().lookup("#devicePane");
        assertNotNull(devicePane, "The small UI's device selection pane should exist");
        assertTrue(devicePane.isVisible(), "The device selection pane should be visible after startup");
    }

    @Test
    @Order(2)
    void a_device_can_be_selected_and_leads_to_the_card_confirmation_screen() throws InterruptedException {
        // The seeded device is at position 1, i.e. the first of the small
        // UI's four fixed device tiles (see MainFormController#devices /
        // DataManager#getDevicesToDisplayXs).
        final Node device1 = robot.lookup("#device1container").query();
        assertNotNull(device1, "The first device tile should exist");
        assertTrue(waitUntil(() -> !device1.getStyleClass().contains("disabled"), Duration.ofSeconds(10)),
                "The seeded, enabled device should not be shown as disabled");

        robot.clickOn(device1);

        // The device has exactly one program, so selecting it skips program
        // selection and goes straight to "waiting for a card" (see
        // MainFormController#onDeviceSelected).
        waitForState(MainFormState.CONFIRMATION_WAIT_FOR_CARD, Duration.ofSeconds(10));
        // Note: the FXML sets an explicit id="confirmation-pane" (hyphenated)
        // on this AnchorPane, which - unlike every other pane here - overrides
        // the CSS id JavaFX's FXMLLoader would otherwise derive from
        // fx:id="confirmationPane", so the lookup selector must match the
        // hyphenated id, not the fx:id.
        final Node confirmationPane = primaryStage.getScene().lookup("#confirmation-pane");
        assertNotNull(confirmationPane);
        assertTrue(confirmationPane.isVisible(), "The confirmation/card pane should be visible");
    }

    @Test
    @Order(3)
    void scanning_a_known_card_completes_the_login_and_reaches_the_ready_state() throws InterruptedException {
        waitForState(MainFormState.CONFIRMATION_WAIT_FOR_CARD, Duration.ofSeconds(10));

        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);

        waitForState(MainFormState.CONFIRMATION_READY, Duration.ofSeconds(10));

        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();
        assertNotNull(controller, "The small UI's controller should be registered with ElwaManager");

        final Node startButton = robot.lookup("#confirmation_buttonStart").query();
        assertTrue(startButton.isVisible(),
                "The 'start program' button should become visible once the card login is complete");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "04" + String.format("%07d", System.currentTimeMillis() % 10_000_000L);
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN " +
                    "(SELECT id FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-Small-%'))");
            s.executeUpdate("DELETE FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-Small-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-Small-%'");
            s.executeUpdate("DELETE FROM programs WHERE name LIKE 'E2E-Small-%'");

            final int programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'FIXED', 3600, 0, 1.50, NULL, NULL, TRUE, 0, TRUE) RETURNING id");
            // Position 1: the small UI's DataManager#getDevicesToDisplayXs
            // maps devices to its four fixed tiles by "position" (1..4).
            final int deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                            "fhem_power_name, deconz_uuid, auto_end_power_threashold, auto_end_wait_time, enabled) " +
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
                            + "'E2E Small Nutzer', 'e2e_small_" + System.currentTimeMillis() + "', '" + cardId + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            s.executeUpdate("UPDATE locations SET client_uid=NULL, client_last_seen=NULL WHERE id=" + locationId);
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
