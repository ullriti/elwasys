package org.kabieror.elwasys.raspiclient.ui;

import javafx.application.Platform;
import javafx.fxml.Initializable;
import org.kabieror.elwasys.common.NoDataFoundException;
import org.kabieror.elwasys.raspiclient.application.ActionContainer;
import org.kabieror.elwasys.raspiclient.application.AlreadyRunningException;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.application.ICloseListener;
import org.kabieror.elwasys.raspiclient.executions.FhemException;
import org.kabieror.elwasys.raspiclient.executions.IExecutionErrorListener;
import org.kabieror.elwasys.raspiclient.executions.IExecutionFinishedListener;
import org.kabieror.elwasys.raspiclient.io.ICardDetectedEventListener;
import org.kabieror.elwasys.raspiclient.ui.scheduler.BacklightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Abstrakter Controller des Hauptfensters
 */
public abstract class AbstractMainFormController implements Initializable, IExecutionFinishedListener,
        IExecutionErrorListener, ICloseListener, ICardDetectedEventListener {

    protected BacklightManager backlightManager;
    private Logger logger = LoggerFactory.getLogger(AbstractMainFormController.class);

    /**
     * Initialisiert das Hauptfenster
     */
    public void initiate() {
        // Auf das Ereignis der Fertigstellung von Programmausführungen hören
        ElwaManager.instance.getExecutionManager().listenToExecutionFinishedEvent(this);
        ElwaManager.instance.getExecutionManager().listenToExecutionErrorEvent(this);

        // Auf das Schließen-Event des Haupfensters hören
        ElwaManager.instance.listenToCloseEvent(this);

        // Auf das Erkennen von Karten hören
        ElwaManager.instance.getCardReader().listenToCardDetectedEvent(this);

        // Hintergrundbeleuchtung nach Inaktivität ausschalten
        this.backlightManager = new BacklightManager();
    }

    /**
     * Visualisiert dem Benutzer einen Fehler im Programmablauf
     *
     * @param title             Der Titel des Fehlers
     * @param detail            Die Beschreibung des Fehlers
     * @param retryAction       Die Aktion, die der Benutzer wiederholen kann
     * @param backOptionEnabled Gibt an, ob der Benutzer zur vorherigen Anzeige zurückkehren können soll
     */
    public abstract void displayError(String title, String detail, ActionContainer retryAction,
                                      boolean backOptionEnabled);

    /**
     * Gibt den Manager für die Hintergrundbeleuchtung zurück.
     *
     * @return Den Manager für die Hintergrundbeleuchtung.
     */
    public BacklightManager getBacklightManager() {
        return this.backlightManager;
    }

    /**
     * Gibt die Nachricht des aktuellen Fehlers zurück.
     *
     * @return Die Nachricht des aktuellen Fehlers.
     */
    public abstract String getCurrentErrorMessage();

    /**
     * Gibt den aktuellen Zustand des Hauptfensters zurück
     *
     * @return Den aktuellen Zustand des Hauptfenster
     */
    public abstract MainFormState getMainFormState();

    /**
     * Versucht, alle Manager zu starten. Im Fehlerfall wird dem Benutzer eine Fehlermeldung angezeigt.
     *
     * @param actionContainer Der ActionContainer, mit welchem der Startvorgang wiederholt werden kann.
     * @return True, wenn der Start erfolgreich war.
     */
    protected boolean tryInitiate(ActionContainer actionContainer) {
        try {
            ElwaManager.instance.initiate();
            return true;
        } catch (final IOException e) {
            this.logger.error("Initialization error.", e);
            Platform.runLater(() -> this
                    .displayError("Start fehlgeschlagen", "Kommunikationsfehler: " + e.getLocalizedMessage(),
                            actionContainer, false));
        } catch (ClassNotFoundException | InterruptedException e) {
            this.logger.error("Initialization error.", e);
            Platform.runLater(() -> this
                    .displayError("Start fehlgeschlagen", "Interner Fehler: " + e.getLocalizedMessage(),
                            actionContainer, false));
        } catch (FhemException e) {
            this.logger.error("Initialization error.", e);
            Platform.runLater(() -> this.displayError("Start fehlgeschlagen",
                    "Kommunikationsfehler: " + exceptionToString(e) +
                            (e.getCause() != null ? "\n" + e.getCause().getLocalizedMessage() : ""), actionContainer,
                    false));
        } catch (NoDataFoundException e) {
            this.logger.error("Data has been removed unexpectedly", e);
            Platform.runLater(() -> this.displayError("Start fehlgeschlagen",
                    "Ein Datensatz wurde unerwartet aus der Datenbank entfernt. Bitte erneut versuchen.",
                    actionContainer, false));
        } catch (AlreadyRunningException e) {
            Platform.runLater(() -> this.displayError("Start fehlgeschlagen",
                    "Die Anwendung wurde mehrfach gestartet.",
                    actionContainer, false));
        } catch (Exception e) {
            this.logger.error("Initialization error.", e);
            Platform.runLater(() -> this.displayError("Start fehlgeschlagen",
                    "Ein unbekannter Fehler ist aufgetreten.",
                    actionContainer, false));
        }
        return false;
    }

    private static String exceptionToString(FhemException e) {
        if (e.getLocalizedMessage() != null) {
            return e.getLocalizedMessage();
        } else {
            return e.getClass().getName();
        }
    }
}
