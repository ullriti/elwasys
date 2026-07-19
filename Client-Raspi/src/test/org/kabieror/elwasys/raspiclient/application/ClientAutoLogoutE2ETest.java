package org.kabieror.elwasys.raspiclient.application;

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
 * Client E2E (test plan C10): after a user logs in, the terminal automatically
 * logs them out again once the configured session timeout elapses without any
 * activity.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientAutoLogoutE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientAutoLogoutE2ETest {

    private static final int FHEM_PORT = 7076;
    private static final int SESSION_TIMEOUT_SECONDS = 3;
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

        workDir = Files.createTempDirectory("elwasys-logout-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "database.server=localhost:5432",
                "database.name=elwasys",
                "database.user=elwaclient1",
                "database.password=elwaclient1",
                "database.useSsl=false",
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=" + SESSION_TIMEOUT_SECONDS,
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "instance.port=8275",
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3595",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-logout-client");
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
    void user_is_logged_out_after_the_session_timeout() throws InterruptedException {
        final MainFormController controller =
                (MainFormController) ElwaManager.instance.getMainFormController();

        // Log in via card scan.
        robot.clickOn(primaryStage.getScene().getRoot());
        robot.write(cardId);
        robot.push(KeyCode.ENTER);
        assertTrue(waitUntil(() -> controller.getRegisteredUser() != null, Duration.ofSeconds(10)),
                "The card holder should be logged in");

        // Without any further activity the user is logged out automatically once
        // the session timeout elapses.
        assertTrue(waitUntil(() -> controller.getRegisteredUser() == null,
                        Duration.ofSeconds(SESSION_TIMEOUT_SECONDS + 12)),
                "The user should be logged out automatically after the session timeout");
    }

    // --- helpers ------------------------------------------------------------

    private static void seedFixtures() throws Exception {
        cardId = "4" + String.format("%08d", System.currentTimeMillis() % 100_000_000L);
        try (Connection c = DriverManager.getConnection(DB_URL, "elwaportal", "elwaportal");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                    + "'E2E Timeout', 'e2e_timeout_" + System.currentTimeMillis() + "', '" + cardId + "', "
                    + groupId + ", FALSE, FALSE, FALSE)");

            s.executeUpdate("UPDATE locations SET client_uid=NULL, client_last_seen=NULL WHERE id=" + locationId);
        }
    }

    private static int queryInt(Statement s, String sql) throws Exception {
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
