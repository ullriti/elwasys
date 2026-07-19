package org.kabieror.elwasys.raspiclient.application;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deepened Client E2E (test plan C2, C3): boots the real application against a
 * seeded database + fhem simulator and drives the terminal through actual
 * usage — the seeded device is listed, and an RFID card "scan" (typed digits,
 * as a keyboard-emulating reader would produce) logs the user in.
 *
 * Fixtures are seeded via JDBC as the elwaportal role (see run-client-e2e.sh,
 * which sets its password). A fresh user with a unique numeric card is created
 * per run so credit_accounting (which is immutable for elwaportal) never
 * accumulates across runs.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientUsageE2ETest)
 * See kb/08-test-plan.md.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ClientUsageE2ETest {

    private static final int FHEM_PORT = 7073;
    private static final String DEVICE_NAME = "E2E-Waschmaschine";
    private static final String PROGRAM_NAME = "E2E-Waschen";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    private static String cardId;
    private static String userName;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-usage-e2e");
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
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3592",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-usage-client");
        System.setProperty("user.dir", workDir.toString());

        primaryStage = FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
        robot = new FxRobot();
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
    void seeded_device_is_listed_after_startup() throws InterruptedException {
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(45));
        assertTrue(waitUntil(() -> sceneContainsText(DEVICE_NAME), Duration.ofSeconds(10)),
                "The seeded device '" + DEVICE_NAME + "' should be shown in the device list");
    }

    @Test
    @Order(2)
    void rfid_card_scan_logs_the_user_in() throws InterruptedException {
        // Ensure we are on the device-selection screen with no user logged in.
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(10));

        // Simulate an RFID reader: it "types" the card number followed by Enter.
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);

        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();
        assertTrue(waitUntil(() -> controller.getRegisteredUser() != null, Duration.ofSeconds(10)),
                "Scanning a known card should log the user in");
        assertNotNull(controller.getRegisteredUser());
        assertTrue(userName.equals(controller.getRegisteredUser().getName()),
                "The logged-in user should be the seeded card owner");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "9" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);
        userName = "E2E Nutzer";
        try (Connection c = DriverManager.getConnection(DB_URL, "elwaportal", "elwaportal");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            // Clean slate for the fixed-name device/program (cascades to rels).
            s.executeUpdate("DELETE FROM devices WHERE name='" + DEVICE_NAME + "'");
            s.executeUpdate("DELETE FROM programs WHERE name='" + PROGRAM_NAME + "'");

            final int programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'FIXED', 3600, 0, 1.50, NULL, NULL, TRUE, 0, TRUE) RETURNING id");
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
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ('"
                            + userName + "', 'e2e_" + System.currentTimeMillis() + "', '" + cardId + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            // Free the location so this client can register.
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

    private static boolean sceneContainsText(String text) {
        final AtomicBoolean found = new AtomicBoolean(false);
        final Parent root = primaryStage.getScene().getRoot();
        searchText(root, text, found);
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
