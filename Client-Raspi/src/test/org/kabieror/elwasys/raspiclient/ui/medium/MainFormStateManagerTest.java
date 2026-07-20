package org.kabieror.elwasys.raspiclient.ui.medium;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.AbortViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.ConfirmationViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.DeviceViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.ErrorViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.StartupViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.ToolbarPaneController;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.UserSettingsViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Charakterisierungstests für die Zustandsmaschine {@link MainFormStateManager}.
 *
 * <p>Bewusst KEIN TestFX/Xvfb-E2E-Test im Sinne der bestehenden Client-Suite (siehe
 * kb/06-ui-tests.md): Hier wird ausschließlich das Zustandsmodell selbst geprüft - ohne
 * Datenbank, ohne {@link org.kabieror.elwasys.raspiclient.application.ElwaManager},
 * ohne echte Stage/Szene und ohne Programmstart über {@code Main}. Die einzige
 * JavaFX-Abhängigkeit ist der Application-Thread, den {@code gotoState()} für die
 * meisten (aber nicht alle) Übergänge intern über {@link Platform#runLater} nutzt.
 *
 * <p>Dieser Test liegt bewusst im selben Package wie {@link MainFormStateManager} und
 * {@link MainFormController}: Beide sind package-private bzw. haben package-private
 * Felder (die Pane-Controller wie {@code devicesPaneController}), auf die hier direkt
 * zugegriffen wird - siehe kb/05-migration-plan.md (Phase 1, "ElwaManager-Singleton per
 * DI entkoppeln"). Der dort eingeführte Test-Konstruktor
 * {@code new MainFormController(false)} überspringt die Kopplung an
 * {@code ElwaManager.instance}: Schon das bloße Referenzieren dieses Singletons würde
 * dessen statischen Initialisierer auslösen (Config-Datei laden, Wartungs-Server
 * starten) - für einen reinen Zustandsautomaten-Test weder nötig noch gewünscht.
 *
 * <p>Die Pane-Controller-Felder werden mit einfachen No-Op-Testdoubles belegt, die die
 * jeweilige konkrete Controller-Klasse erweitern und nur die von
 * {@link MainFormStateManager} tatsächlich aufgerufenen Methoden überschreiben. Damit
 * werden die (in echten Instanzen @FXML-injizierten, hier aber null bleibenden) UI-Felder
 * nie angefasst.
 */
class MainFormStateManagerTest {

    private static final ToolbarState NOOP_TOOLBAR_STATE = new ToolbarState(null, null, null, null);

    private MainFormController controller;
    private MainFormStateManager stateManager;

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS), "JavaFX-Toolkit wurde nicht rechtzeitig gestartet.");
    }

    @BeforeEach
    void setUp() {
        // false: siehe Klassen-Javadoc - überspringt jede Berührung von ElwaManager.instance.
        this.controller = new MainFormController(false);
        this.controller.startupPaneController = new NoOpStartupViewController();
        this.controller.devicesPaneController = new NoOpDeviceViewController();
        this.controller.confirmationPaneController = new NoOpConfirmationViewController();
        this.controller.abortPaneController = new NoOpAbortViewController();
        this.controller.errorPaneController = new NoOpErrorViewController();
        this.controller.toolbarPaneController = new NoOpToolbarPaneController();
        this.controller.userSettingsController = new NoOpUserSettingsViewController();
        this.stateManager = this.controller.stateManager;
    }

    @Test
    void startsInInitState() {
        assertEquals(MainFormState.INIT, this.stateManager.getState());
    }

    @Test
    void startupToSelectDeviceIsAllowed() throws InterruptedException {
        gotoStateAndAwait(MainFormState.STARTUP);
        assertEquals(MainFormState.STARTUP, this.stateManager.getState());

        gotoStateAndAwait(MainFormState.SELECT_DEVICE);
        assertEquals(MainFormState.SELECT_DEVICE, this.stateManager.getState());
        assertEquals(MainFormState.STARTUP, this.stateManager.getPreviousState());
    }

    @Test
    void selectDeviceToConfirmationIsAllowed() throws InterruptedException {
        goToSelectDevice();

        gotoStateAndAwait(MainFormState.CONFIRMATION);
        assertEquals(MainFormState.CONFIRMATION, this.stateManager.getState());
        assertEquals(MainFormState.SELECT_DEVICE, this.stateManager.getPreviousState());
    }

    @Test
    void selectDeviceToConfirmProgramAbortionIsAllowed() throws InterruptedException {
        goToSelectDevice();

        gotoStateAndAwait(MainFormState.CONFIRM_PROGRAM_ABORTION);
        assertEquals(MainFormState.CONFIRM_PROGRAM_ABORTION, this.stateManager.getState());
        assertEquals(MainFormState.SELECT_DEVICE, this.stateManager.getPreviousState());
    }

    @Test
    void confirmProgramAbortionToSelectDeviceIsAllowed() throws InterruptedException {
        goToSelectDevice();
        gotoStateAndAwait(MainFormState.CONFIRM_PROGRAM_ABORTION);

        gotoStateAndAwait(MainFormState.SELECT_DEVICE);
        assertEquals(MainFormState.SELECT_DEVICE, this.stateManager.getState());
        assertEquals(MainFormState.CONFIRM_PROGRAM_ABORTION, this.stateManager.getPreviousState());
    }

    @Test
    void confirmationToSelectDeviceIsAllowed() throws InterruptedException {
        goToSelectDevice();
        gotoStateAndAwait(MainFormState.CONFIRMATION);

        gotoStateAndAwait(MainFormState.SELECT_DEVICE);
        assertEquals(MainFormState.SELECT_DEVICE, this.stateManager.getState());
        assertEquals(MainFormState.CONFIRMATION, this.stateManager.getPreviousState());
    }

    @Test
    void disallowedTransitionThrowsIllegalStateException() throws InterruptedException {
        // STARTUP -> CONFIRMATION ist nicht registriert (nur STARTUP -> SELECT_DEVICE ist es).
        gotoStateAndAwait(MainFormState.STARTUP);

        assertThrows(IllegalStateException.class, () -> this.stateManager.gotoState(MainFormState.CONFIRMATION));
        // Der verweigerte Übergang darf den Zustand nicht verändert haben.
        assertEquals(MainFormState.STARTUP, this.stateManager.getState());
    }

    @Test
    void errorStateIsReachableFromSelectDevice() throws InterruptedException {
        goToSelectDevice();

        // ERROR wird synchron gesetzt (kein Platform.runLater im gotoState()-Code für diesen Fall).
        this.stateManager.gotoState(MainFormState.ERROR);
        assertEquals(MainFormState.ERROR, this.stateManager.getState());
    }

    @Test
    void errorStateIsReachableFromStartup() throws InterruptedException {
        gotoStateAndAwait(MainFormState.STARTUP);

        this.stateManager.gotoState(MainFormState.ERROR);
        assertEquals(MainFormState.ERROR, this.stateManager.getState());
    }

    @Test
    void errorStateIsReachableFromInit() {
        // INIT ist der Ausgangszustand direkt nach dem Konstruktor - auch von hier aus
        // muss ERROR erreichbar sein ("von jedem Zustand aus").
        this.stateManager.gotoState(MainFormState.ERROR);
        assertEquals(MainFormState.ERROR, this.stateManager.getState());
    }

    @Test
    void gotoStateBeforeErrorReturnsToStateBeforeError() throws InterruptedException {
        goToSelectDevice();
        gotoStateAndAwait(MainFormState.CONFIRMATION);

        this.stateManager.gotoState(MainFormState.ERROR);
        assertEquals(MainFormState.ERROR, this.stateManager.getState());

        this.stateManager.gotoStateBeforeError();
        assertEquals(MainFormState.CONFIRMATION, this.stateManager.getState());
    }

    @Test
    void resetAfterErrorGoesToSelectDevice() throws InterruptedException {
        goToSelectDevice();
        gotoStateAndAwait(MainFormState.CONFIRMATION);

        this.stateManager.gotoState(MainFormState.ERROR);
        assertEquals(MainFormState.ERROR, this.stateManager.getState());

        runAndAwaitFx(this.stateManager::resetAfterError);
        assertEquals(MainFormState.SELECT_DEVICE, this.stateManager.getState());
    }

    private void goToSelectDevice() throws InterruptedException {
        gotoStateAndAwait(MainFormState.STARTUP);
        gotoStateAndAwait(MainFormState.SELECT_DEVICE);
    }

    private void gotoStateAndAwait(MainFormState newState) throws InterruptedException {
        runAndAwaitFx(() -> this.stateManager.gotoState(newState));
    }

    /**
     * Führt {@code action} aus und wartet anschließend, bis der JavaFX-Application-Thread
     * alles abgearbeitet hat, was {@code gotoState()} dabei ggf. über
     * {@link Platform#runLater} eingereiht hat. Funktioniert, weil JavaFX Runnables in
     * FIFO-Reihenfolge abarbeitet: Der hier nachgereihte Latch-Countdown läuft garantiert
     * erst, nachdem ein zuvor von {@code action} eingereihtes Runnable fertig ist.
     */
    private static void runAndAwaitFx(Runnable action) throws InterruptedException {
        action.run();
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX-Application-Thread ist nicht rechtzeitig nachgekommen.");
    }

    // ---- Test-Doubles -----------------------------------------------------------------
    //
    // Einfache No-Op-Implementierungen, die MainFormStateManager als Pane-Controller
    // genügen, ohne die (hier null bleibenden) @FXML-Felder der echten Controller
    // anzufassen oder auf ElwaManager.instance zuzugreifen.

    private static class NoOpStartupViewController extends StartupViewController {
        @Override
        public void onActivate() {
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onReturnFromError() {
        }

        @Override
        public ToolbarState getToolbarState() {
            return NOOP_TOOLBAR_STATE;
        }
    }

    private static class NoOpDeviceViewController extends DeviceViewController {
        @Override
        public void onActivate() {
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onReturnFromError() {
        }

        @Override
        public ToolbarState getToolbarState() {
            return NOOP_TOOLBAR_STATE;
        }
    }

    private static class NoOpConfirmationViewController extends ConfirmationViewController {
        @Override
        public void onActivate() {
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onReturnFromError() {
        }

        @Override
        public ToolbarState getToolbarState() {
            return NOOP_TOOLBAR_STATE;
        }
    }

    private static class NoOpAbortViewController extends AbortViewController {
        @Override
        public void onActivate() {
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onReturnFromError() {
        }

        @Override
        public ToolbarState getToolbarState() {
            return NOOP_TOOLBAR_STATE;
        }
    }

    private static class NoOpErrorViewController extends ErrorViewController {
        @Override
        public void onActivate() {
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onReturnFromError() {
        }

        @Override
        public ToolbarState getToolbarState() {
            return NOOP_TOOLBAR_STATE;
        }
    }

    private static class NoOpUserSettingsViewController extends UserSettingsViewController {
        @Override
        public void onActivate() {
        }

        @Override
        public void onDeactivate() {
        }

        @Override
        public void onReturnFromError() {
        }

        @Override
        public ToolbarState getToolbarState() {
            return NOOP_TOOLBAR_STATE;
        }
    }

    private static class NoOpToolbarPaneController extends ToolbarPaneController {
        @Override
        public void setToolbarState(ToolbarState tbs) {
        }
    }
}
