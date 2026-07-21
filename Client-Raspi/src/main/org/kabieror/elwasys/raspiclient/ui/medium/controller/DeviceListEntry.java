package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.kabieror.elwasys.common.FormatUtilities;
import org.kabieror.elwasys.common.ProgramType;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientUser;
import org.kabieror.elwasys.raspiclient.application.ActionContainer;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.devices.IDeviceRegistrationService;
import org.kabieror.elwasys.raspiclient.executions.IExecutionErrorListener;
import org.kabieror.elwasys.raspiclient.executions.IExecutionFinishedListener;
import org.kabieror.elwasys.raspiclient.executions.IExecutionStartedListener;
import org.kabieror.elwasys.raspiclient.ui.ComponentControlInstance;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.UiUtilities;
import org.kabieror.elwasys.raspiclient.ui.medium.IViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;
import org.kabieror.elwasys.raspiclient.ui.scheduler.InactivityFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller eines Listeneintrags in der Übersicht aller Geräte
 *
 * @author Oliver Kabierschke
 */
public class DeviceListEntry implements Initializable, IViewController, IExecutionStartedListener,
        IExecutionFinishedListener, IExecutionErrorListener {

    private static final DateTimeFormatter endDateFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT);
    private final Integer LOCK = 0;
    private final Logger logger = LoggerFactory.getLogger(DeviceListEntry.class);

    /**
     * Der aktuelle Zustand der Gerätekachel
     */
    private DeviceListEntryState state = DeviceListEntryState.FREE;

    /**
     * Das von dieser Komponente dargestellte Gerät
     */
    private ClientDevice device;

    /**
     * Der Aktualisierungsvorgang der laufenden Ausführung
     */
    private ScheduledFuture updateFuture = null;

    /**
     * Die aktuell laufende Programmausführung
     */
    private ClientExecution runningExecution = null;

    /**
     * Der aktuelle Fehler eines fehlgeschlagenen Programm-Abschlusses
     */
    private Exception currentException;

    /**
     * Indiziert, ob ein Tür-Öffnung-Vorgang angestoßen wurde
     */
    private boolean openDoorTriggered = false;
    private boolean cancelOpenDoorTriggered = false;

    private StringProperty deviceName = new SimpleStringProperty("");
    private StringProperty statusText = new SimpleStringProperty("frei");
    private StringProperty lastUserName = new SimpleStringProperty("Niemand");
    private StringProperty remainingTime = new SimpleStringProperty("0:00");
    private StringProperty endDate = new SimpleStringProperty(endDateFormatter.format(LocalDateTime.now()));
    private StringProperty errorText = new SimpleStringProperty();
    private StringProperty disabledText = new SimpleStringProperty();

    private DeviceViewController deviceViewController;
    private MainFormController mainFormController;

    private Runnable errorRetryAction;

    private InactivityFuture errorRetryFuture;

    @FXML
    private Pane deviceListEntry;
    @FXML
    private VBox doorOpenButton;
    @FXML
    private VBox selectButton;
    @FXML
    private VBox abortButton;
    @FXML
    private VBox doorStatusButton;
    @FXML
    private VBox errorInfoButton;
    @FXML
    private VBox errorRetryButton;
    @FXML
    private VBox registerButton;
    @FXML
    private Label remainingCaption;
    @FXML
    private HBox remainingContainer;
    @FXML
    private Label endDateCaption;
    @FXML
    private Label endDateLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label disabledLabel;


    /**
     * Listener für eine Änderung des angemeldeten Benutzers
     */
    private ChangeListener<ClientUser> registeredUserChangedListener = (observable, oldValue, newValue) -> {
        this.applyUserStyle(newValue);
    };

    private ScheduledExecutorService deviceRegistrationScheduler = Executors.newSingleThreadScheduledExecutor();

    private IDeviceRegistrationService registrationService;

    public DeviceListEntry() {

    }

    /**
     * Erzeugt eine neue Instanz eines DeviceListEntry.
     */
    static ComponentControlInstance<DeviceListEntry> createInstance() {
        try {
            FXMLLoader loader = new FXMLLoader(DeviceListEntry.class
                    .getResource("/org/kabieror/elwasys/raspiclient/ui/medium/components/DeviceListEntry.fxml"));
            return new ComponentControlInstance<>(loader.load(), loader.getController());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Setzt initial den Controller dieses Eintrags. Muss vor Aufruf von onStart() aufgerufen werden.
     *
     * @param deviceViewController Der DeviceViewController, welcher diesem Eintrag übergeordnet ist.
     */
    void setDeviceViewController(DeviceViewController deviceViewController) {
        this.deviceViewController = deviceViewController;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    @Override
    public void onStart(MainFormController mfc) {
        this.mainFormController = mfc;
        this.registrationService = ElwaManager.instance.getDeviceRegistrationService();

        // Letzter Benutzer (bereits Teil der Geräteübersicht, siehe
        // DeviceOverviewDto#lastUserName - kein eigener Aufruf mehr nötig).
        if (this.device.getLastUserName() != null) {
            this.lastUserName.set(this.device.getLastUserName());
        }

        ElwaManager.instance.getExecutionManager().listenToExecutionStartedEvent(this);
        ElwaManager.instance.getExecutionManager().listenToExecutionFinishedEvent(this);
        ElwaManager.instance.getExecutionManager().listenToExecutionErrorEvent(this);
        this.mainFormController.registeredUserProperty().addListener(registeredUserChangedListener);

        // Setze Status
        this.refresh(true);

        // Zeige bei Start laufende Ausführung an
        if (this.device.getCurrentExecution() != null) {
            this.onExecutionStarted(this.device.getCurrentExecution());
        }
    }

    @Override
    public void onTerminate() {
        synchronized (LOCK) {
            this.logger.debug(String.format("Terminating view of device '%1s'", this.device.getName()));
            ElwaManager.instance.getExecutionManager().stopListenToExecutionStartedEvent(this);
            ElwaManager.instance.getExecutionManager().stopListenToExecutionFinishedEvent(this);
            ElwaManager.instance.getExecutionManager().stopListenToExecutionErrorEvent(this);
            this.mainFormController.registeredUserProperty().removeListener(registeredUserChangedListener);

            if (this.updateFuture != null && !this.updateFuture.isDone()) {
                this.updateFuture.cancel(true);
            }
        }
    }

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public void onReturnFromError() {
        if (this.openDoorTriggered) {
            // Deaktiviere Doppelklick-Sperre für den Fall eines vorausgegangenen Fehlers
            this.doorOpenButton.setDisable(false);
            this.openDoorTriggered = false;
        }
        if (this.cancelOpenDoorTriggered) {
            // Deaktiviere Doppelklick-Sperre für den Fall eines vorausgegangenen Fehlers
            this.doorStatusButton.setDisable(false);
            this.cancelOpenDoorTriggered = false;
        }
    }

    @Override
    public ToolbarState getToolbarState() {
        return null;
    }

    /**
     * Behandle den Start einer Programmausführung, falls diese auf dem hier dargestellten Gerät stattfindet.
     *
     * @param e Die gestartete Programmausführung.
     */
    @Override
    public void onExecutionStarted(ClientExecution e) {
        if (e.getDevice() != this.device || updateFuture != null) {
            return;
        }

        Platform.runLater(() -> {
            synchronized (LOCK) {
                this.runningExecution = e;

                if (e.getProgram().getType() == ProgramType.OPEN_DOOR) {
                    // Tür-Öffnen Programm gestartet
                    // Aktualisiere Aussehen
                    this.state = DeviceListEntryState.DOOR_OPENED;
                    // Setzte Doppelklick-Schutz zurück
                    this.doorStatusButton.setDisable(false);

                    this.openDoorTriggered = false;
                } else {
                    // Reguläres Programm gestartet
                    this.state = DeviceListEntryState.OCCUPIED;
                }

                // Regelmäßige Aktualisierung der verbleibenden Zeit
                this.updateFuture = this.mainFormController.getUpdateService().scheduleAtFixedRate(() -> {
                    Platform.runLater(() -> this.remainingTime.set(FormatUtilities
                            .formatDuration(e.getRemainingTime(), e.getProgram().getType() != ProgramType.OPEN_DOOR)));
                }, 0, 1, TimeUnit.SECONDS);

                this.refresh(true);
            }
        });
    }

    @Override
    public void onExecutionFinished(ClientExecution e) {
        if (e.getDevice() != this.device || updateFuture == null) {
            return;
        }

        Platform.runLater(() -> {
            synchronized (LOCK) {
                if (this.errorRetryFuture != null) {
                    this.errorRetryFuture.cancel();
                    this.errorRetryFuture = null;
                }
                this.updateFuture.cancel(false);
                this.updateFuture = null;
                this.runningExecution = null;

                if (e.getProgram().getType() == ProgramType.OPEN_DOOR) {
                    // Tür-Öffnen Programm beendet
                    this.state = DeviceListEntryState.FREE;
                    this.applyUserStyle(this.mainFormController.getRegisteredUser());

                    this.cancelOpenDoorTriggered = false;
                } else {
                    // Reguläres Programm beendet
                    this.state = DeviceListEntryState.FREE;
                    this.applyUserStyle(this.mainFormController.getRegisteredUser());
                }
            }
        });
    }

    @Override
    public void onExecutionFailed(ClientExecution execution, Exception exception) {
        if (execution == this.runningExecution) {
            Platform.runLater(() -> {
                this.displayError(exception.getLocalizedMessage(), exception, () -> {
                    final Thread actionThread = new Thread(() -> {
                        try {
                            ElwaManager.instance.getExecutionManager().retryFinishExecution(this.runningExecution);
                        } catch (final Exception e) {
                            this.logger.error("Could not finish the execution " + this.runningExecution.getId(), e);
                            Platform.runLater(() -> this.errorText.set(e.getLocalizedMessage()));
                            this.currentException = e;
                        } finally {
                            this.mainFormController.endWait();
                        }
                    });
                    this.mainFormController.beginWait();
                    actionThread.start();
                });
            });
            // Erneuten Versuch planen
            this.errorRetryFuture = this.mainFormController.getInactivityScheduler().scheduleJob(() -> {
                Platform.runLater(() -> {
                    synchronized (LOCK) {
                        if (this.mainFormController.getMainFormState() != MainFormState.SELECT_DEVICE ||
                                this.mainFormController.isWaiting()) {
                            this.logger.debug("Retrying to finish execution is paused while not in normal state.");
                            return;
                        }
                    }
                    this.logger.info("Automatically retrying to finish failed execution now.");
                    this.onErrorRetry(null);
                });
            }, 30, TimeUnit.SECONDS, -1);
            this.errorRetryFuture.setName("DeviceListEntry." + this.device.getName() + ".RetryErrorJob");
        }
    }

    /**
     * Aktualisiert die Anzeige dieses Gerätes
     */
    void refresh() {
        synchronized (LOCK) {
            this.refresh(false);
        }
    }

    private void refresh(boolean force) {
        if (!force && this.device.getCurrentExecution() != null) {
            // Keine Aktualisierung wenn eine Ausführung läuft oder ein Benutzer angemeldet ist
            return;
        }

        if (this.state == DeviceListEntryState.UNREGISTERED) {
            this.state = DeviceListEntryState.FREE;
        }

        if (this.registrationService != null && !registrationService.isDeviceRegistered(this.device)) {
            this.state = DeviceListEntryState.UNREGISTERED;
        } else if (this.device.isEnabled() && this.state == DeviceListEntryState.DISABLED) {
            // Gerät nur aktivieren, wenn es zuvor deaktiviert war
            this.state = DeviceListEntryState.FREE;
        } else if (!this.device.isEnabled()) {
            this.state = DeviceListEntryState.DISABLED;
        }

        this.deviceName.set(this.device.getName());

        switch (this.state) {
            case FREE:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", true);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-disabled", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-unregistered", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-error", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "locked", false);
                this.deviceListEntry.setDisable(false);
                this.doorOpenButton.setDisable(false);
                this.selectButton.setDisable(true);
                this.statusText.set("frei");
                break;
            case FREE_AVAILABLE:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", true);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-disabled", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-unregistered", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-error", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "locked", false);
                this.deviceListEntry.setDisable(false);
                this.doorOpenButton.setDisable(false);
                this.selectButton.setDisable(false);
                this.statusText.set("frei");
                break;
            case DOOR_OPENED:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", true);
                this.selectButton.setDisable(true);
                this.statusText.set("Tür freigegeben");
                break;
            case FREE_BLOCKED:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", true);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-disabled", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-unregistered", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-error", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "locked", true);
                this.deviceListEntry.setDisable(true);
                this.selectButton.setDisable(true);
                this.statusText.set("nicht verfügbar");
                break;
            case OCCUPIED:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", true);
                this.lastUserName.set(this.runningExecution.getUser().getName());
                this.statusText.set("belegt");
                this.endDate.set(endDateFormatter.format(this.runningExecution.getEndDate()));
                break;
            case ERROR:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-error", true);
                this.statusText.set("FEHLER");
                this.errorRetryButton.setDisable(errorRetryAction == null);
                break;
            case DISABLED:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-disabled", true);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-unregistered", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-error", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "locked", false);
                this.deviceListEntry.setDisable(true);
                this.statusText.set("deaktiviert");
            case UNREGISTERED:
                UiUtilities.setStyleClass(this.deviceListEntry, "status-free", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-disabled", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-unregistered", true);
                UiUtilities.setStyleClass(this.deviceListEntry, "status-error", false);
                UiUtilities.setStyleClass(this.deviceListEntry, "locked", false);
                this.deviceListEntry.setDisable(false);
                this.statusText.set("Keine Steckdose");
        }
    }

    /**
     * Wechselt den Zustand der Gerätekachel zur Anzeige eines Fehlers
     *
     * @param message          Die anzuzeigende Fehlermeldung
     * @param exception        Der Fehler, der auf der Detail-Seite angezeigt werden soll
     * @param errorRetryAction Die Aktion, die die fehlgeschlagene Aktion wiederholt
     */
    private void displayError(String message, Exception exception, Runnable errorRetryAction) {
        synchronized (LOCK) {
            this.errorRetryAction = errorRetryAction;
            this.currentException = exception;
            this.errorText.set(message);

            this.state = DeviceListEntryState.ERROR;
            this.refresh(true);
        }
    }

    /**
     * Aktualisiert das Aussehen passend zum angemeldeten Benutzer.
     *
     * @param user Der angemeldete Benutzer.
     */
    private void applyUserStyle(ClientUser user) {
        if (this.state != DeviceListEntryState.FREE && this.state != DeviceListEntryState.FREE_AVAILABLE &&
                this.state != DeviceListEntryState.FREE_BLOCKED) {
            // Keine Aktualisierung bei besetztem Gerät
            return;
        }
        if (user != null && user.isDeviceUsable(this.device.getId())) {
            // Benutzer darf das Gerät benutzen
            this.state = DeviceListEntryState.FREE_AVAILABLE;
            this.refresh(false);
        } else if (user != null) {
            // Für den Benutzer ist das Gerät gesperrt
            this.state = DeviceListEntryState.FREE_BLOCKED;
            this.refresh(false);
        } else {
            // Kein Benutzer angemeldet
            this.state = DeviceListEntryState.FREE;
            this.refresh();
        }
    }

    /**
     * Wird nach einem Klick des Benutzers auf die Schaltfläche "Tür freigeben" aufgerufen.
     */
    public void onOpenDoor(MouseEvent mouseEvent) {
        synchronized (LOCK) {
            // Aktiviere Doppelklick-Schutz
            this.doorOpenButton.setDisable(true);
            this.openDoorTriggered = true;
        }
        this.deviceViewController.onOpenDoor(this.device);
    }

    /**
     * Wird nach einem Klick des Benutzers auf die Schaltfläche "Gerät buchen" aufgerufen.
     */
    public void onSelectDevice(MouseEvent mouseEvent) {
        this.deviceViewController.onDeviceSelected(this.device);
    }

    /**
     * Bricht das laufende Türöffnungs-Programm ab.
     */
    public void onCancelDoorOpened(MouseEvent mouseEvent) {
        // Aktiviere Doppelklick-Schutz
        this.doorStatusButton.setDisable(true);
        this.cancelOpenDoorTriggered = true;

        Thread t = new Thread(() -> ElwaManager.instance.getExecutionManager().abortExecution(this.runningExecution));
        t.setName("doorCloseThread");
        t.start();
    }

    /**
     * Fragt den Benutzer, ob der die Ausführung wirklich abbrechen möchte.
     */
    public void onConfirmCancelExecution(MouseEvent mouseEvent) {
        this.mainFormController.setExecutionToAbort(this.runningExecution);
        this.mainFormController.gotoState(MainFormState.CONFIRM_PROGRAM_ABORTION);
    }

    /**
     * Versucht erneut, die fehlgeschlagene Programmausführung zu beenden
     */
    public void onErrorRetry(MouseEvent mouseEvent) {
        synchronized (LOCK) {
            if (this.errorRetryAction != null) {
                this.errorRetryAction.run();
            }
        }
    }

    /**
     * Zeigt mehr Infos zum aufgetretenen Fehler an
     */
    public void onErrorInfo(MouseEvent mouseEvent) {
        ActionContainer ac = new ActionContainer();
        ac.setAction(() -> {
            final Thread actionThread = new Thread(() -> {
                try {
                    ElwaManager.instance.getExecutionManager().retryFinishExecution(this.runningExecution);
                } catch (final IOException e) {
                    this.logger.error("Could not finish the execution " + this.runningExecution.getId(), e);
                    this.mainFormController.displayError("Kommunikationsfehler", e.getLocalizedMessage(), ac, true);
                } catch (final Exception e) {
                    this.logger.error("Could not finish the execution " + this.runningExecution.getId(), e);
                    this.mainFormController.displayError("Interner Fehler", e.getLocalizedMessage(), ac, true);
                } finally {
                    this.mainFormController.endWait();
                }
            });
            this.mainFormController.beginWait();
            actionThread.start();
        });
        if (this.currentException instanceof IOException) {
            this.mainFormController
                    .displayError("Kommunikationsfehler", this.currentException.getLocalizedMessage(), ac, true);
        } else {
            this.mainFormController
                    .displayError("Interner Fehler", this.currentException.getLocalizedMessage(), ac, true);
        }
    }

    public void onRegister(MouseEvent event) {
        deviceRegistrationScheduler.submit(() -> {
            logger.info("Scanning for new actor for device " + device.getId());
            UiUtilities.setStyleClass(registerButton, "active", true);
            ElwaManager.instance.getDeviceRegistrationService()
                    .registerDevice(this.device)
                    .join();
            UiUtilities.setStyleClass(registerButton, "active", false);
            refresh(true);
        });
    }

    public void setDevice(ClientDevice device) {
        this.device = device;
    }

    /**
     * Property: deviceName
     */
    public String getDeviceName() {
        return deviceName.get();
    }

    public void setDeviceName(String deviceName) {
        this.deviceName.set(deviceName);
    }

    public StringProperty deviceNameProperty() {
        return deviceName;
    }

    /**
     * Property: StatusText
     */
    public String getStatusText() {
        return statusText.get();
    }

    public void setStatusText(String statusText) {
        this.statusText.set(statusText);
    }

    public StringProperty statusTextProperty() {
        return statusText;
    }


    /**
     * Property: LastUserName
     */
    public String getLastUserName() {
        return lastUserName.get();
    }

    public void setLastUserName(String lastUserName) {
        this.lastUserName.set(lastUserName);
    }

    public StringProperty lastUserNameProperty() {
        return lastUserName;
    }


    /**
     * Property: Remaining Time
     */
    public String getRemainingTime() {
        return remainingTime.get();
    }

    public void setRemainingTime(String remainingTime) {
        this.remainingTime.set(remainingTime);
    }

    public StringProperty remainingTimeProperty() {
        return remainingTime;
    }


    /**
     * Property: End Date
     */
    public String getEndDate() {
        return endDate.get();
    }

    public void setEndDate(String endDate) {
        this.endDate.set(endDate);
    }

    public StringProperty endDateProperty() {
        return endDate;
    }

    /**
     * Property: Error Text
     */
    public String getErrorText() {
        return errorText.get();
    }

    public void setErrorText(String errorText) {
        this.errorText.set(errorText);
    }

    public StringProperty errorTextProperty() {
        return errorText;
    }

    /**
     * Property: Disabled Text
     */
    public String getDisabledText() {
        return disabledText.get();
    }

    public void setDisabledText(String disabledText) {
        this.disabledText.set(disabledText);
    }

    public StringProperty disabledTextProperty() {
        return disabledText;
    }

    /**
     * Die möglichen Zustände des Eintrages
     */
    private enum DeviceListEntryState {
        /**
         * Gerät ist frei und kein Benutzer ist angemeldet
         */
        FREE,

        /**
         * Gerät ist frei und für den angemeldeten Benutzer verfügbar
         */
        FREE_AVAILABLE,

        /**
         * Gerät ist frei, aber für den angemeldeten Benutzer gesperrt
         */
        FREE_BLOCKED,

        /**
         * Die Tür ist freigegeben
         */
        DOOR_OPENED,

        /**
         * Auf dem Gerät wird ein Programm ausgeführt
         */
        OCCUPIED,

        /**
         * Fehlerzustand
         */
        ERROR,

        /**
         * Das Gerät ist deaktiviert
         */
        DISABLED,

        /**
         * Unregistered
         */
        UNREGISTERED,
    }
}
