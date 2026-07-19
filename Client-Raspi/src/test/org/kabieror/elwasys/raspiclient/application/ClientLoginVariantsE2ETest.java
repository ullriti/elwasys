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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client login-variant E2E (test plan C6–C8): the terminal must reject cards
 * that are unknown, belong to a blocked user, or belong to a user whose group
 * is not permitted at this location — showing the corresponding toolbar notice
 * and NOT logging anyone in.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientLoginVariantsE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientLoginVariantsE2ETest {

    private static final int FHEM_PORT = 7074;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static Path workDir;
    private static Stage primaryStage;
    private static FxRobot robot;

    private static String blockedCard;
    private static String foreignCard;
    private static final String UNKNOWN_CARD = "8" + String.format("%08d", System.nanoTime() % 100_000_000L);

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-variants-e2e");
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
                "instance.port=8273",
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3593",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-variants-client");
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
    void unknown_card_is_rejected() throws InterruptedException {
        scan(UNKNOWN_CARD);
        assertNoLoginWithMarker("card-unknown");
    }

    @Test
    void blocked_user_card_is_rejected() throws InterruptedException {
        scan(blockedCard);
        assertNoLoginWithMarker("user-blocked");
    }

    @Test
    void user_from_disallowed_group_is_rejected() throws InterruptedException {
        scan(foreignCard);
        assertNoLoginWithMarker("location-disallowed");
    }

    // --- helpers ------------------------------------------------------------

    private void scan(String card) {
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(card);
        robot.push(KeyCode.ENTER);
    }

    private void assertNoLoginWithMarker(String styleMarker) throws InterruptedException {
        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();
        // The toolbar signals the rejection by adding a style class to #userInfo
        // (the notices themselves are shown/hidden via CSS keyed on that class).
        assertTrue(waitUntil(() -> userInfoHasStyle(styleMarker), Duration.ofSeconds(5)),
                "The toolbar should visualize '" + styleMarker + "' for the rejected card");
        assertNull(controller.getRegisteredUser(), "No user should be logged in for a rejected card");
    }

    private static boolean userInfoHasStyle(String styleClass) {
        final Node userInfo = primaryStage.getScene().lookup("#userInfo");
        return userInfo != null && userInfo.getStyleClass().contains(styleClass);
    }

    private static void seedFixtures() throws Exception {
        final long id = System.currentTimeMillis();
        blockedCard = "7" + String.format("%08d", id % 100_000_000L);
        foreignCard = "6" + String.format("%08d", (id + 1) % 100_000_000L);
        try (Connection c = DriverManager.getConnection(DB_URL, "elwaportal", "elwaportal");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int allowedGroup = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            // A group that is NOT among the location's permitted groups.
            final int foreignGroup = insertReturningId(s,
                    "INSERT INTO user_groups (name) VALUES ('E2E-Fremd-" + id + "') RETURNING id");

            // Blocked user (permitted group, but blocked).
            s.executeUpdate("INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) " +
                    "VALUES ('E2E Gesperrt', 'e2e_blocked_" + id + "', '" + blockedCard + "', " + allowedGroup +
                    ", FALSE, TRUE, FALSE)");
            // User from a group that is not allowed at this location.
            s.executeUpdate("INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) " +
                    "VALUES ('E2E Fremd', 'e2e_foreign_" + id + "', '" + foreignCard + "', " + foreignGroup +
                    ", FALSE, FALSE, FALSE)");

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
