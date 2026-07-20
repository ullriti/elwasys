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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client E2E (test plan C14): a time-based (DYNAMIC) program shows its pricing
 * breakdown — base fee ("Grundgebühr") and time price ("Zeitpreis") — on the
 * confirmation screen.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientDynamicProgramE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientDynamicProgramE2ETest {

    private static final int FHEM_PORT = 7080;
    private static final String DEVICE_NAME = "E2E-WM-Dyn";
    private static final String PROGRAM_NAME = "E2E-Dynamisch";
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

        workDir = Files.createTempDirectory("elwasys-dyn-e2e");
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
                "instance.port=8279",
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3599",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-dyn-client");
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
    void dynamic_program_shows_its_pricing_breakdown() throws Exception {
        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();

        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);
        assertTrue(waitUntil(() -> controller.getRegisteredUser() != null, Duration.ofSeconds(10)),
                "The card holder should be logged in");

        assertTrue(waitUntil(() -> isEnabled(selectButtonFor(DEVICE_NAME)), Duration.ofSeconds(10)),
                "The 'book device' button should be enabled");
        robot.clickOn(selectButtonFor(DEVICE_NAME));
        waitForState(MainFormState.CONFIRMATION, Duration.ofSeconds(10));

        // The dynamic program lists its base fee and time price.
        assertTrue(waitUntil(() -> sceneContainsText("Grundgebühr"), Duration.ofSeconds(10)),
                "A dynamic program should show its base fee (Grundgebühr)");
        assertTrue(sceneContainsText("Zeitpreis"),
                "A dynamic program should show its time price (Zeitpreis)");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "1" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);
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

            // A DYNAMIC program: base fee (flagfall) + rate per time unit.
            final int programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'DYNAMIC', 3600, 0, 0.50, 0.10, 'MINUTES', TRUE, 0, TRUE) RETURNING id");
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
                            + "'E2E Dyn', 'e2e_dyn_" + System.currentTimeMillis() + "', '" + cardId + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            s.executeUpdate("UPDATE locations SET client_uid=NULL, client_last_seen=NULL WHERE id=" + locationId);
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

    private static boolean containsText(Node node, String text) {
        final AtomicBoolean found = new AtomicBoolean(false);
        searchText(node, text, found);
        return found.get();
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
