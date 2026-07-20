package org.kabieror.elwasys.raspiclient.application;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client E2E (test plan C15): if the database cannot be reached at startup, the
 * client does not crash — it boots the UI and enters the error state so an
 * operator can retry (AbstractMainFormController.tryInitiate catches the
 * SQLException and shows a "Start fehlgeschlagen" error).
 *
 * We point the client at a port where no database is listening (localhost:5433)
 * so the connection is refused immediately, and assert MainFormState.ERROR.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientDatabaseErrorE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientDatabaseErrorE2ETest {

    private static Path workDir;

    @BeforeAll
    static void launchWithUnreachableDatabase() throws Exception {
        workDir = Files.createTempDirectory("elwasys-dberror-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                // Nothing listens on 5433 → connection refused immediately.
                "database.server=localhost:5433",
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
                "fhem.port=7082",
                "instance.port=8281",
                "smtp.server=",
                "smtp.port=465",
                "smtp.useSSL=false",
                "smtp.senderAddress=noreply@example.com",
                "maintenance.server=localhost",
                "maintenance.port=3601",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-dberror-client");
        System.setProperty("user.dir", workDir.toString());

        FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
    }

    @AfterAll
    static void shutdown() {
        try {
            FxToolkit.cleanupStages();
        } catch (Exception ignored) {
            // best effort
        }
    }

    @Test
    void an_unreachable_database_lands_in_the_error_state() throws Exception {
        assertTrue(waitUntil(() -> {
            final var c = ElwaManager.instance.getMainFormController();
            return c != null && c.getMainFormState() == MainFormState.ERROR;
        }, Duration.ofSeconds(45)), "An unreachable database at startup should lead to the ERROR state");

        // The app must never have reached the normal device-selection screen.
        assertTrue(ElwaManager.instance.getMainFormController().getMainFormState() == MainFormState.ERROR,
                "The client should stay in the ERROR state, not proceed to device selection");
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
