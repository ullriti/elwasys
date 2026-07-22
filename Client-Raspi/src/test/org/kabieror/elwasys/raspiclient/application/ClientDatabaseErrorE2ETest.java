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
 * Client E2E (test plan C15, Phase 4 AP4 - siehe kb/05-migration-plan.md "Client-Cutover"):
 * ist das Backend beim Start nicht erreichbar, stürzt der Client nicht ab, sondern bootet
 * die UI und geht in den Fehlerzustand, sodass ein Bediener es erneut versuchen kann
 * ({@code AbstractMainFormController#tryInitiate} fängt die von {@code ElwaManager#initiate}
 * geworfene {@code ApiException} - eine {@link java.io.IOException}-Unterklasse - über den
 * bestehenden {@code catch (IOException e)}-Zweig ab und zeigt "Kommunikationsfehler" an).
 * <p>
 * Vor dem Cutover (Phase 4 AP3) prüfte dieser Test einen unerreichbaren DATENBANK-Port - das
 * primäre Fehlerbild ist jetzt ein unerreichbares BACKEND (die Datenbank bleibt nur noch
 * transitional für die Fernwartungs-Registrierung/{@link LocationManager} relevant, siehe
 * {@link ElwaManager#initiate}, und bleibt in diesem Test daher regulär erreichbar, damit
 * ausschließlich der Backend-Erreichbarkeits-Check fehlschlägt).
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientDatabaseErrorE2ETest)
 * See kb/08-test-plan.md.
 */
public class ClientDatabaseErrorE2ETest {

    private static Path workDir;

    @BeforeAll
    static void launchWithUnreachableBackend() throws Exception {
        workDir = Files.createTempDirectory("elwasys-dberror-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                // Nichts hört auf diesem Port -> die Verbindung wird sofort verweigert.
                "backend.url=http://localhost:1/",
                "backend.token=irrelevant-since-unreachable",
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=7082",
                "instance.port=8281",
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
    void an_unreachable_backend_lands_in_the_error_state() throws Exception {
        assertTrue(waitUntil(() -> {
            final var c = ElwaManager.instance.getMainFormController();
            return c != null && c.getMainFormState() == MainFormState.ERROR;
        }, Duration.ofSeconds(45)), "An unreachable backend at startup should lead to the ERROR state");

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
