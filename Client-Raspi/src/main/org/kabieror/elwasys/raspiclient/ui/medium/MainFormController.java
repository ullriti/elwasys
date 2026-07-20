package org.kabieror.elwasys.raspiclient.ui.medium;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import org.kabieror.elwasys.common.*;
import org.kabieror.elwasys.raspiclient.application.ActionContainer;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.executions.IExecutionStartedListener;
import org.kabieror.elwasys.raspiclient.io.CardDetectedEvent;
import org.kabieror.elwasys.raspiclient.ui.AbstractMainFormController;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.*;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ErrorState;
import org.kabieror.elwasys.raspiclient.ui.medium.state.IMainFormStateListener;
import org.kabieror.elwasys.raspiclient.ui.scheduler.InactivityFuture;
import org.kabieror.elwasys.raspiclient.ui.scheduler.InactivityScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.SQLException;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Dieser Controller steuert die Geräteauswahl.
 *
 * @author Oliver Kabierschke
 */
@SuppressWarnings("serial")
public class MainFormController extends AbstractMainFormController implements IMainFormStateListener,
        IExecutionStartedListener {
    // Package-private (statt private): ermöglicht MainFormStateManagerTest im selben
    // Package den direkten Zugriff auf den Zustandsautomaten, ohne einen Getter für
    // reinen Testgebrauch in die Produktions-API aufzunehmen.
    final MainFormStateManager stateManager;

    @FXML
    StartupViewController startupPaneController;
    @FXML
    DeviceViewController devicesPaneController;
    @FXML
    ConfirmationViewController confirmationPaneController;
    @FXML
    AbortViewController abortPaneController;
    @FXML
    ErrorViewController errorPaneController;
    @FXML
    ToolbarPaneController toolbarPaneController;
    @FXML
    UserSettingsViewController userSettingsController;
    @FXML
    private WaitPaneController waitPaneController;

    /**
     * Service, der regelmäßig Aufgaben ausführt (Update der Geräte-Liste)
     */
    private ScheduledExecutorService updateService;

    private InactivityScheduler inactivityScheduler;

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Ausgewählte Daten des Benutzers
     */
    private Device selectedDevice;
    private ObjectProperty<User> registeredUser = new SimpleObjectProperty<>();

    /**
     * Aktueller Fehlerzustand
     */
    private ErrorState errorState;
    /**
     * Service, der die Warte-Seite anzeigt.
     */
    private ScheduledExecutorService waitPaneService;
    private InactivityFuture runningLogoutDelay;

    /**
     * Die Aktion, die fehlgeschlagen ist und wiederholt werden kann
     */
    private Runnable retryAction;
    private Execution executionToAbort;

    /**
     * Gibt an, ob gegenwärtig auf die Fertigstellung einer Aktion gewartet wird.
     */
    private boolean waiting;

    /**
     * Konstruktor
     */
    public MainFormController() {
        this(true);
    }

    /**
     * Konstruktor.
     * <p>
     * Der Parameter erlaubt es, den Controller aufzubauen, ohne den {@link ElwaManager}-
     * Singleton anzufassen. Das ist nötig, weil bereits das bloße Referenzieren von
     * {@code ElwaManager.instance} dessen statischen Initialisierer auslöst (Laden der
     * Konfigurationsdatei, Start des Wartungs-Servers) – für isolierte Unit-Tests des
     * Zustandsautomaten ({@link MainFormStateManager}, siehe MainFormStateManagerTest im
     * selben Package) ist das weder nötig noch gewünscht. In Produktion wird ausschließlich
     * der öffentliche No-Arg-Konstruktor verwendet (durch den FXMLLoader), der unverändert
     * {@code wireToElwaManager=true} übergibt.
     *
     * @param wireToElwaManager Ob die Kopplung an {@link ElwaManager#instance} hergestellt
     *                          werden soll (Produktion: {@code true}). Package-private
     *                          nutzbar mit {@code false} nur für Tests in diesem Package.
     */
    MainFormController(boolean wireToElwaManager) {
        if (wireToElwaManager) {
            // Registriere beim Manager
            ElwaManager.instance.setMainFormController(this);
        }

        this.updateService = Executors.newScheduledThreadPool(4);
        this.waitPaneService = Executors.newSingleThreadScheduledExecutor();

        // Zustandsmanager initiieren
        this.stateManager = new MainFormStateManager(this);

        this.inactivityScheduler = new InactivityScheduler();

        if (wireToElwaManager) {
            // Auto-Logout initiieren
            this.registeredUser.addListener((observable, oldValue, newValue) -> {
                if (this.runningLogoutDelay != null) {
                    this.runningLogoutDelay.cancel();
                }
                if (newValue != null) {
                    this.runningLogoutDelay = this.inactivityScheduler.scheduleJob(() -> Platform.runLater(() -> {
                        // Bei Fehlerzustand darf keine Veränderung am angemeldeten Benutzer vorgenommen werden
                        if (this.stateManager.getState() == MainFormState.ERROR) {
                            this.logger.warn("Cannot auto-logout user while in error state");
                        } else if (this.waiting) {
                            this.logger.warn("Cannot auto-logout user while waiting for an action to finish");
                        } else {
                            this.registeredUser.set(null);
                        }
                    }), ElwaManager.instance.getConfigurationManager().getAutoLogoutSeconds(), TimeUnit.SECONDS, -1);
                    this.runningLogoutDelay.setName("MainFormController.LogoutJob");
                }
            });
        }
    }

    /**
     * Wird aufgerufen, sobald das Hauptformular geladen ist
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // ElwaManager initiieren
        final ActionContainer actionContainer = new ActionContainer();
        final Runnable startupRunnable = () -> {
            Platform.runLater(() -> MainFormController.this.stateManager.gotoState(MainFormState.STARTUP));
            try {
                // Startup Delay
                Thread.sleep(ElwaManager.instance.getConfigurationManager().getStartupDelay().getSeconds() * 1000);
            } catch (final InterruptedException e1) {
                this.logger.warn("Interrupted while performing startup delay.");
            }
            final Logger logger = LoggerFactory.getLogger(this.getClass());
            logger.info("Initializing components");

            if (super.tryInitiate(actionContainer)) {
                Platform.runLater(() -> MainFormController.this.stateManager.gotoState(MainFormState.SELECT_DEVICE));
            }
        };
        actionContainer.setAction(() -> {
            final Thread startupThread = new Thread(startupRunnable);
            startupThread.start();
        });
        actionContainer.getAction().run();

        // Bereite essentielle Seiten vor, die auch bei nicht erfolgreichem Programmstart zur Verfügung stehem müssen
        this.startupPaneController.onStart(this);
        this.errorPaneController.onStart(this);
    }

    /**
     * Initiiert den Controller. Setzt voraus, dass der ElwaManager bereits
     * initiiert wurde
     */
    public void initiate() {
        super.initiate();

        Platform.runLater(() -> {
            this.abortPaneController.onStart(this);
            this.confirmationPaneController.onStart(this);
            this.devicesPaneController.onStart(this);
            this.toolbarPaneController.onStart(this);
            this.waitPaneController.onStart(this);
            this.userSettingsController.onStart(this);
        });

        ElwaManager.instance.getExecutionManager().listenToExecutionStartedEvent(this);
    }

    /**
     * Gibt den aktuellen Zustand des Haupt-Fensters zurück.
     *
     * @return Den aktuellen Zustand des Hauptfesnters
     */
    public MainFormState getMainFormState() {
        return this.stateManager.getState();
    }

    public InactivityScheduler getInactivityScheduler() {
        return inactivityScheduler;
    }

    /**
     * Gibt die Nachricht des aktuellen Fehlers zurück.
     *
     * @return Die Nachricht des aktuellen Fehlers.
     */
    public String getCurrentErrorMessage() {
        if (this.stateManager.getState() != MainFormState.ERROR) {
            return "";
        } else {
            return this.errorState.getErrorTitle();
        }
    }

    /**
     * Zeigt einen Fehler an
     *
     * @param title  Titel
     * @param detail Details
     */
    private void displayError(String title, String detail, ActionContainer backAction) {
        this.errorState =
                new ErrorState(title, detail + "\nBitte Log-Datei prüfen, um mehr über diesen Fehler zu erfahren.",
                        backAction != null ? () -> backAction.getAction().run() : null, null);
        Platform.runLater(() -> this.stateManager.gotoState(MainFormState.ERROR));
    }

    /**
     * Zeigt einen Fehler an und bietet das Wiederholen der Aktion an
     *
     * @param title       Titel
     * @param detail      Details
     * @param backAction  Die Aktion, die die Anwendung zum Zustand vor den Fehler zurück setzt.
     * @param retryAction Die wiederholbare Aktion
     */
    public void displayError(String title, String detail, ActionContainer backAction, ActionContainer retryAction) {
        this.errorState =
                new ErrorState(title, detail + "\nBitte Log-Datei prüfen, um mehr über diesen Fehler zu erfahren.",
                        backAction != null ? () -> {
                            this.stateManager.gotoStateBeforeError();
                            backAction.getAction().run();
                        } : null, retryAction != null ? () -> {
                    this.stateManager.gotoStateBeforeError();
                    retryAction.getAction().run();
                } : null);
        Platform.runLater(() -> this.stateManager.gotoState(MainFormState.ERROR));
    }

    @Override
    public void displayError(String title, String detail, ActionContainer retryAction, boolean backOptionEnabled) {
        this.errorState =
                new ErrorState(title, detail + "\nBitte Log-Datei prüfen, um mehr über diesen Fehler zu erfahren.",
                        backOptionEnabled ? this.stateManager::gotoStateBeforeError : null,
                        retryAction != null ? () -> {
                            this.stateManager.gotoStateBeforeError();
                            retryAction.getAction().run();
                        } : null);
        Platform.runLater(() -> this.stateManager.gotoState(MainFormState.ERROR));
    }

    /**
     * Shows the wait panel
     */
    public void beginWait() {
        this.waiting = true;
        Platform.runLater(() -> this.waitPaneController.onActivate());
    }

    /**
     * Hides the wait panel
     */
    public void endWait() {
        this.waiting = false;
        Platform.runLater(() -> this.waitPaneController.onDeactivate());
    }

    /**
     * Prüft, ob derzeitig die Warte-Anzeige aktiv ist.
     */
    public boolean isWaiting() {
        return this.waiting;
    }

    /**
     * Aktualisiert den Zustand der Toolbar.
     */
    public void updateToolbar() {
        this.stateManager.upateToolbarState();
    }

    /**
     * Zeigt die Benutzereinstellungen an
     */
    public void showUserSettings() {
        if (this.registeredUser.get() != null) {
            this.stateManager.showUserSettings();
        }
    }

    /**
     * Versteckt die Benutzereinstellungen
     */
    public void hideUserSettings() {
        this.stateManager.hideUserSettings();
    }

    /**
     * Wird aufgerufen, sobald das Haupfenster geschlossen werden soll
     */
    @Override
    public void onClose(boolean restart) {
        this.updateService.shutdownNow();
        this.waitPaneService.shutdownNow();
        this.inactivityScheduler.shutdown();
        if (restart) {
            this.updateService = Executors.newScheduledThreadPool(4);
            this.waitPaneService = Executors.newSingleThreadScheduledExecutor();
            this.inactivityScheduler = new InactivityScheduler();
        }

        Platform.runLater(() -> {
            this.abortPaneController.onTerminate();
            this.confirmationPaneController.onTerminate();
            this.devicesPaneController.onTerminate();
            this.toolbarPaneController.onTerminate();
            this.waitPaneController.onTerminate();
            this.userSettingsController.onTerminate();
        });
    }

    /**
     * Wird aufgerufen, wenn sich der Zustand des Hauptfensters geändert hat.
     */
    @Override
    public void onMainFormStateChanged(MainFormState oldState, MainFormState newState) {
        if (newState == MainFormState.SELECT_DEVICE) {
            // Geräteauswahl zurücksetzen
            this.selectedDevice = null;
        }
    }

    /**
     * Wird aufgerufen, sobald eine Karte erkannt wird
     */
    @Override
    public void onCardDetected(CardDetectedEvent e) {
        if (this.stateManager.getState() == MainFormState.ERROR) {
            this.logger.warn("Cannot log in user while in error state");
            return;
        }
        // Suche den zur Karte passenden Benutzer
        final ActionContainer actionContainer = new ActionContainer();
        actionContainer.setAction(() -> {
            final Runnable searchUserRunnable = () -> {
                final User newUser;
                try {
                    newUser = ElwaManager.instance.getDataRetriever().getUserByCardId(e.getCardId());
                } catch (final SQLException e1) {
                    this.logger.error("SQLException while looking up user.", e1);
                    Platform.runLater(() -> {
                        this.displayError("Datenbankfehler", e1.getLocalizedMessage(), actionContainer, true);
                        this.endWait();
                    });
                    return;
                }
                Location location = null;
                try {
                    location = ElwaManager.instance.getLocation();
                } catch (SQLException e1) {
                    this.logger.error("Could not load the location of this client.", e1);
                    Platform.runLater(() -> {
                        this.displayError("Datenbankfehler", e1.getLocalizedMessage(), actionContainer, true);
                        this.endWait();
                    });
                    return;
                } catch (NoDataFoundException e1) {
                    this.logger.error("Could not load the location of this client.", e1);
                    Platform.runLater(() -> {
                        this.displayError("Standort gelöscht",
                                "Dieser Standort wurde im elwaPortal gelöscht und steht nicht weiter zur Verfügung.",
                                actionContainer, false);
                        this.endWait();
                    });
                    return;
                }
                if (newUser == null) {
                    // Kein Benutzer zur Id gefunden.
                    this.logger.warn("There is no user associated to card " + e.getCardId() + ".");
                    Platform.runLater(() -> {
                        this.registeredUser.set(null);
                        this.toolbarPaneController.visualizeUnknownId();
                        this.endWait();
                    });
                } else if (newUser.isBlocked()) {
                    this.logger.info("Blocked user " + newUser.getName() + " tried to log in.");
                    Platform.runLater(() -> {
                        this.registeredUser.set(null);
                        this.toolbarPaneController.visualizeBlockedUser();
                        this.endWait();
                    });
                } else if (!location.getValidUserGroups().contains(newUser.getGroup())) {
                    this.logger.info("User " + newUser.getName() + " is not allowed to use this location");
                    Platform.runLater(() -> {
                        this.registeredUser.set(null);
                        this.toolbarPaneController.visualizeLocationNotAllowed();
                        this.endWait();
                    });
                } else {
                    this.logger.info("User logged in: " + newUser.getName());
                    Platform.runLater(() -> {
                        this.registeredUser.set(newUser);
                        this.endWait();
                    });
                }
            };

            Platform.runLater(this::beginWait);

            final Thread searchUser = new Thread(searchUserRunnable);
            searchUser.setName("CardDetectedThread");
            searchUser.start();
        });

        actionContainer.getAction().run();
    }

    @Override
    public void onExecutionStarted(Execution e) {
        this.updateUser();
    }

    @Override
    public void onExecutionFinished(Execution e) {
        this.updateUser();
    }

    @Override
    public void onExecutionFailed(Execution execution, Exception exception) {
        this.updateUser();
    }

    private void updateUser() {
        if (this.registeredUser.get() != null) {
            try {
                this.registeredUser.get().update();
                if (this.registeredUser.get().isDeleted()) {
                    this.registeredUser.set(null);
                }
            } catch (NoDataFoundException e1) {
                this.logger
                        .warn("The user has been deleted since the beginning of the execution! Aborting newly started" +
                                " execution.");
                this.registeredUser.set(null);
            } catch (SQLException e1) {
                this.logger.error("Could not update user.", e1);
            }
        }
    }

    /**
     * Leitet die Buchung eines Gerätes ein.
     *
     * @param device Das zu buchende Gerät.
     */
    public void onDeviceSelected(Device device) {
        this.selectedDevice = device;
        this.stateManager.gotoState(MainFormState.CONFIRMATION);
    }

    /**
     * Wechselt den Zustand
     *
     * @param newState Der neue Zustand
     */
    public void gotoState(MainFormState newState) {
        this.stateManager.gotoState(newState);
    }

    /**
     * Gibt das vom Benutzer ausgewählte Gerät zurück
     *
     * @return Das ausgewählte Gerät
     */
    public Device getSelectedDevice() {
        return selectedDevice;
    }

    /**
     * Property: RegisteredUser
     */
    public User getRegisteredUser() {
        return registeredUser.get();
    }

    public void setRegisteredUser(User registeredUser) {
        this.registeredUser.set(registeredUser);
    }

    public ObjectProperty<User> registeredUserProperty() {
        return registeredUser;
    }

    /**
     * Gibt den aktuellen Fehlerzustand zurück (sofern es einen gibt)
     */
    public ErrorState getErrorState() {
        return errorState;
    }

    /**
     * Gibt den Update-Service zurück.
     *
     * @return Den Update-Service.
     */
    public ScheduledExecutorService getUpdateService() {
        return updateService;
    }

    public Execution getExecutionToAbort() {
        return executionToAbort;
    }

    public void setExecutionToAbort(Execution executionToAbort) {
        this.executionToAbort = executionToAbort;
    }

}
