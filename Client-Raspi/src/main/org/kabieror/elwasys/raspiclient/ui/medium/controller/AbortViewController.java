package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.application.ActionContainer;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.executions.IExecutionFinishedListener;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.medium.IViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller für die Seite zum Programmabbruch.
 */
public class AbortViewController implements Initializable, IViewController, IExecutionFinishedListener {

    public Node confirmAbortionPane;

    private Logger logger = LoggerFactory.getLogger(AbortViewController.class);
    private MainFormController mainFormController;
    private ClientExecution execution;
    private ToolbarState toolbarState =
            new ToolbarState("Zurück", "Bestätigen", this::onToolbarBack, this::onToolbarConfirm);

    /**
     * Initialisiert die Komponenten nachdem die Oberfläche dieser Komponente geladen ist
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    /**
     * Wird aufgerufen, sobald alle Manager geladen sind und die Benutzeroberfläche mit Daten befüllt werden soll.
     *
     * @param mainFormController Der Haupt-Controller.
     */
    public void onStart(MainFormController mainFormController) {
        this.mainFormController = mainFormController;
    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onActivate() {
        this.execution = this.mainFormController.getExecutionToAbort();
        if (this.execution == null || !this.execution.isRunning()) {
            // Wenn das Programm nicht ausgeführt wird, breche ab.
            this.mainFormController.displayError("Interner Fehler",
                    "Die abzubrechende Programmausführung wird derzeit nicht ausgeführt.",
                    new ActionContainer(() -> this.mainFormController.gotoState(MainFormState.SELECT_DEVICE)), null);
        } else {
            this.confirmAbortionPane.setVisible(true);
        }

        ElwaManager.instance.getExecutionManager().listenToExecutionFinishedEvent(this);
    }

    @Override
    public void onDeactivate() {
        this.execution = null;
        this.confirmAbortionPane.setVisible(false);

        ElwaManager.instance.getExecutionManager().stopListenToExecutionFinishedEvent(this);
    }

    @Override
    public void onReturnFromError() {

    }

    @Override
    public ToolbarState getToolbarState() {
        return this.toolbarState;
    }

    private void onToolbarConfirm() {
        this.mainFormController.beginWait();
        final ActionContainer actionContainer = new ActionContainer();
        actionContainer.setAction(() -> {
            final Thread t = new Thread(() -> {
                try {
                    ElwaManager.instance.getExecutionManager().abortExecution(this.execution);
                    this.mainFormController.gotoState(MainFormState.SELECT_DEVICE);
                } catch (final Exception ex) {
                    this.logger.error("Could not abort the running execution.", ex);
                    Platform.runLater(() -> this.mainFormController
                            .displayError("Interner Fehler", ex.getLocalizedMessage(), actionContainer, true));
                } finally {
                    Platform.runLater(() -> this.mainFormController.endWait());
                }
            });
            this.mainFormController.beginWait();
            t.start();
        });
        actionContainer.getAction().run();
    }

    private void onToolbarBack() {
        this.mainFormController.gotoState(MainFormState.SELECT_DEVICE);
    }

    @Override
    public void onExecutionFinished(ClientExecution e) {
        if (e == this.execution) {
            this.mainFormController.gotoState(MainFormState.SELECT_DEVICE);
        }
    }
}
