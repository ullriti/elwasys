package org.kabieror.elwasys.raspiclient.ui.medium;

import javafx.application.Platform;
import org.kabieror.elwasys.raspiclient.ui.IMainFormStateManager;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.MainFormStateTransition;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Diese Klasse verwaltet den Zustandsautomaten des Hauptfensters
 *
 * @author Oliver Kabierschke
 */
class MainFormStateManager implements IMainFormStateManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Der Controller des Hauptfensters
     */
    private final MainFormController controller;
    /**
     * Erlaubte Zustandsübergänge
     */
    private final Map<MainFormStateTransition, Function<MainFormState, Boolean>> stateTransitions =
            new HashMap<>();
    /**
     * Der aktive Controller
     */
    private IViewController activeController;

    /**
     * Aktueller Zustand
     */
    private MainFormState state;

    /**
     * Der letzte Zustand
     */
    private MainFormState previousState;

    /**
     * Der Zustand des Haupformulars vorm Fehlerzustand
     */
    private MainFormState stateBeforeError;

    /**
     * @param controller
     */
    public MainFormStateManager(MainFormController controller) {
        this.controller = controller;

        // Zustandsübergänge hinzufügen
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.STARTUP, MainFormState.SELECT_DEVICE),
                        (newState) -> {
                            this.doTransition(this.controller.devicesPaneController);
                            return true;
                        });
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.SELECT_DEVICE, MainFormState.CONFIRMATION), (newState) -> {
                    this.doTransition(this.controller.confirmationPaneController);
                    return true;
                });
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.SELECT_DEVICE, MainFormState.CONFIRM_PROGRAM_ABORTION),
                        (newState) -> {
                            this.doTransition(this.controller.abortPaneController);
                            return true;
                        });
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRM_PROGRAM_ABORTION, MainFormState.SELECT_DEVICE),
                        (newState) -> {
                            this.doTransition(this.controller.devicesPaneController);
                            return true;
                        });
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION, MainFormState.SELECT_DEVICE), (newState) -> {
                    this.doTransition(this.controller.devicesPaneController);
                    return true;
                });

        this.state = MainFormState.INIT;
    }

    /**
     * Gibt den aktuellen Zustand des Hauptformulars zurück
     *
     * @return Den aktuellen Zustand des Hauptformulars
     */
    public MainFormState getState() {
        return this.state;
    }

    public MainFormState getPreviousState() {
        return this.previousState;
    }

    /**
     * Führt eine Zustandsänderung des Hauptformulars durch, falls erlaubt
     *
     * @param newState Neuer Zustand
     * @throws IllegalStateException
     */
    public void gotoState(MainFormState newState) {
        this.logger.trace("Changing state: " + this.state.name() + " -> " + newState.name());
        if (newState.equals(this.state)) {
            // Selber Zustand
            return;
        }

        // Fehlerzustände behandeln
        if (newState.equals(MainFormState.ERROR)) {
            // Fehlerzustand ist von jedem anderen aus erreichbar
            this.controller.errorPaneController.onActivate();

            this.applyToolbarState(this.controller.errorPaneController.getToolbarState());

            this.stateBeforeError = this.state;
            this.state = newState;
            return;
        } else if (this.state.equals(MainFormState.ERROR)) {
            // Aus Fehlerzuständen kann nur durch Aufruf der Funktion resetAfterError() oder gotoStateBeforeError()
            // zurückgekehrt werden.
            return;
        }

        if (newState.equals(MainFormState.STARTUP)) {
            // Start-Zustand ist von jedem anderen aus erreichbar
            this.doTransition(this.controller.startupPaneController);
            this.state = newState;
            return;
        }

        Function<MainFormState, Boolean> transMethod = this.stateTransitions.get(new MainFormStateTransition(this.state, newState));
        if (transMethod == null) {
            transMethod = this.stateTransitions.get(new MainFormStateTransition(this.state, MainFormState.ANY));
        }
        if (transMethod == null) {
            transMethod = this.stateTransitions.get(new MainFormStateTransition(MainFormState.ANY, newState));
        }
        if (transMethod == null) {
            // Zustandsänderung nicht erlaubt
            throw new IllegalStateException(
                    "Die Zustandsänderung von '" + this.state.name() + "' nach '" + newState.name() +
                            "' ist nicht erlaubt.");
        } else {
            // Zustandsänderung durchführen
            if (transMethod != null) {
                final Function<MainFormState, Boolean> finalTransMethod = transMethod;
                Platform.runLater(() -> {
                    if (finalTransMethod.apply(newState)) {
                        this.previousState = this.state;
                        this.state = newState;
                        if (newState.equals(MainFormState.SELECT_DEVICE)) {
                            // Bedienbereit erreicht -> Readiness-Marker fuer den Auto-Update-Watchdog (Phase 6 AP5).
                            org.kabieror.elwasys.raspiclient.application.TerminalReadinessMarker.markReady();
                        }
                    } else {
                        this.logger.error("Die Zustandsänderung von '" + this.state.name() + "' nach '" + newState.name() +
                                "' ist nicht möglich.");
                    }
                });
            }
        }
    }

    /**
     * Wendet einen neuen Zustand der Toolbar an.
     *
     * @param tbs Der neue Zustand der Toolbar.
     */
    private void applyToolbarState(ToolbarState tbs) {
        this.controller.toolbarPaneController.setToolbarState(tbs);
    }

    /**
     * Aktualisiert den Zustand der Toolbar.
     */
    public void upateToolbarState() {
        applyToolbarState(this.activeController.getToolbarState());
    }

    /**
     * Geht zum Zustand zurück, der vor dem aufgetretenen Fehler vorherrschte
     */
    public void gotoStateBeforeError() {
        this.logger.trace("Going back to non-error state");
        this.controller.errorPaneController.onDeactivate();
        this.applyToolbarState(this.activeController.getToolbarState());
        this.state = this.stateBeforeError;
        this.activeController.onReturnFromError();
    }

    /**
     * Geht nach einem Fehler zum Startzustand
     */
    public void resetAfterError() {
        this.controller.errorPaneController.onDeactivate();
        this.state = this.stateBeforeError;
        this.gotoState(MainFormState.SELECT_DEVICE);
    }

    /**
     * Geht zu anderer Ansicht.
     *
     * @param newController Die neue Ansicht
     */
    private void doTransition(IViewController newController) {
        if (this.activeController != null) {
            this.activeController.onDeactivate();
        }
        newController.onActivate();
        this.applyToolbarState(newController.getToolbarState());
        this.activeController = newController;
    }

    void showUserSettings() {
        controller.userSettingsController.onActivate();
        this.applyToolbarState(controller.userSettingsController.getToolbarState());
    }

    void hideUserSettings() {
        controller.userSettingsController.onDeactivate();
        this.applyToolbarState(this.activeController.getToolbarState());
    }
}
