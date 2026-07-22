package org.kabieror.elwasys.raspiclient.application;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end test that launches the <em>real</em> Raspberry-Pi client
 * application ({@link Main}) headlessly and drives it through its actual
 * startup path: configuration loading, backend REST-API/token connection,
 * gateway connection and the JavaFX UI state machine.
 *
 * To make this possible without real hardware, the test:
 *  - points the client at the test backend ({@link TestBackend}, REST API +
 *    Standort-Token - since Phase 4 AP4/AP5 the client no longer connects to
 *    the database directly, see docs/kb/05-migration-plan.md),
 *  - runs the in-project {@link FhemSimulator} as a fake gateway (the fhem
 *    path is used because no deConz server is configured),
 *  - writes an elwasys.properties into a temporary working directory.
 *
 * Run headless under Xvfb:
 *   ./run-client-e2e.sh
 * or directly:
 *   xvfb-run -a mvn -f Client-Raspi/pom.xml test -Dtest=ClientAppE2ETest
 *
 * See docs/kb/06-ui-tests.md.
 */
public class ClientAppE2ETest {

    private static final int FHEM_PORT = 7072;

    private static FhemSimulator fhem;
    private static Path workDir;

    @BeforeAll
    static void launchRealApplication() throws Exception {
        // 1. Fake fhem gateway
        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        // 2. Working directory with an elwasys.properties the client will load.
        //    user.dir must be set before WashguardConfiguration is class-loaded
        //    (which happens when the ElwaManager singleton is first constructed
        //    during Main.start()).
        workDir = Files.createTempDirectory("elwasys-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "backend.url=" + TestBackend.url(),
                "backend.token=" + TestBackend.token(),
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=60",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "maintenance.server=localhost",
                "maintenance.port=3591",
                ""));
        // Stable client UID (read by WashguardConfiguration, used to identify this
        // terminal on the outgoing maintenance WebSocket connection, see
        // TerminalWebSocketClient) - not strictly required for this test to pass, but
        // keeps repeated runs deterministic instead of a fresh random UID each time.
        Files.writeString(workDir.resolve(".client-uid"), "e2e-test-client");
        System.setProperty("user.dir", workDir.toString());

        // 3. Launch the real JavaFX application.
        FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
    }

    @AfterAll
    static void shutdown() throws Exception {
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
    void application_starts_up_and_reaches_device_selection() throws InterruptedException {
        final MainFormState state = waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(45));
        assertEquals(MainFormState.SELECT_DEVICE, state,
                "The client should complete startup (DB + fhem gateway) and reach the device selection screen");
    }

    /**
     * Polls the main form state until it reaches {@code target}, failing fast if
     * the application enters an error state.
     */
    private static MainFormState waitForState(MainFormState target, Duration timeout)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        MainFormState last = null;
        while (System.currentTimeMillis() < deadline) {
            final var controller = ElwaManager.instance.getMainFormController();
            if (controller != null) {
                last = controller.getMainFormState();
                if (last == target) {
                    return last;
                }
                if (last == MainFormState.ERROR || last == MainFormState.ERROR_RETRYABLE) {
                    fail("The application entered an error state during startup: "
                            + controller.getCurrentErrorMessage());
                }
            }
            Thread.sleep(250);
        }
        return last;
    }
}
