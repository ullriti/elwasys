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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
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
    /**
     * Monitor für alle Zustands-/Anzeige-Mutationen dieser Kachel. Bewusst ein eigenes
     * Object statt der früheren autoboxten {@code Integer}-Konstante: deren Wert stammt aus
     * dem JVM-weiten Integer-Cache und wäre damit als Monitor mit fremdem Code geteilt (#91).
     */
    private final Object lock = new Object();
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

    /**
     * Der Steckdosen-Suchlauf dieser Kachel (#91, siehe {@link DeviceRegistrationScan}).
     */
    private final DeviceRegistrationScan registrationScan = new DeviceRegistrationScan();

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
        synchronized (this.lock) {
            this.logger.debug(String.format("Terminating view of device '%1s'", this.device.getName()));
            ElwaManager.instance.getExecutionManager().stopListenToExecutionStartedEvent(this);
            ElwaManager.instance.getExecutionManager().stopListenToExecutionFinishedEvent(this);
            ElwaManager.instance.getExecutionManager().stopListenToExecutionErrorEvent(this);
            this.mainFormController.registeredUserProperty().removeListener(registeredUserChangedListener);

            if (this.updateFuture != null && !this.updateFuture.isDone()) {
                this.updateFuture.cancel(true);
            }
            // Den Executor des Steckdosen-Suchlaufs mit beenden (#91) - er blieb bisher je
            // Kachel und je Restart als Thread zurück.
            this.registrationScan.stop();
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
            synchronized (this.lock) {
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
            synchronized (this.lock) {
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
            this.scheduleAutomaticFinishRetry();
        }
    }

    /**
     * Plant den zyklischen, automatischen Wiederholversuch für einen fehlgeschlagenen
     * Programm-Abschluss (alle 30s Inaktivität, unbegrenzt oft). Aus
     * {@link #onExecutionFailed(ClientExecution, Exception)} herausgelöst (#91), Verhalten
     * unverändert: der Versuch pausiert, solange das Hauptfenster nicht in der Geräteauswahl
     * ist oder auf eine andere Aktion wartet.
     */
    private void scheduleAutomaticFinishRetry() {
        this.errorRetryFuture = this.mainFormController.getInactivityScheduler().scheduleJob(() -> {
            Platform.runLater(() -> {
                synchronized (this.lock) {
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

    /**
     * Aktualisiert die Anzeige dieses Gerätes
     */
    void refresh() {
        synchronized (this.lock) {
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

        this.applyAppearance(APPEARANCES.get(this.state));

        // Zustandsspezifische Angaben, die aus Laufzeitdaten stammen und darum nicht Teil der
        // statischen Tabelle sein können.
        switch (this.state) {
            case OCCUPIED -> {
                this.lastUserName.set(this.runningExecution.getUser().getName());
                this.endDate.set(endDateFormatter.format(this.runningExecution.getEndDate()));
            }
            case ERROR -> this.errorRetryButton.setDisable(this.errorRetryAction == null);
            default -> {
                // Alle übrigen Zustände sind vollständig über die Tabelle beschrieben.
            }
        }
    }

    /**
     * Wendet eine Zeile der Zustandstabelle auf die Kachel an.
     */
    private void applyAppearance(EntryAppearance appearance) {
        if (appearance.resetAllStyleClasses()) {
            this.resetStatusStyleClasses();
        }
        for (String styleClass : appearance.stylesOff()) {
            UiUtilities.setStyleClass(this.deviceListEntry, styleClass, false);
        }
        for (String styleClass : appearance.stylesOn()) {
            UiUtilities.setStyleClass(this.deviceListEntry, styleClass, true);
        }
        if (appearance.entryDisabled() != null) {
            this.deviceListEntry.setDisable(appearance.entryDisabled());
        }
        if (appearance.doorOpenButtonDisabled() != null) {
            this.doorOpenButton.setDisable(appearance.doorOpenButtonDisabled());
        }
        if (appearance.selectButtonDisabled() != null) {
            this.selectButton.setDisable(appearance.selectButtonDisabled());
        }
        this.statusText.set(appearance.statusText());
    }

    /**
     * Setzt alle Status-Style-Klassen der Gerätekachel zurück. Zentralisiert die zuvor in
     * FREE/FREE_AVAILABLE/FREE_BLOCKED/DISABLED/UNREGISTERED wiederholte Reset-Boilerplate
     * (#82) - jeder dieser Zustände bestimmt den kompletten Klassensatz neu, anders als
     * DOOR_OPENED/ERROR, die nur einzelne Klassen gezielt umschalten.
     */
    private void resetStatusStyleClasses() {
        UiUtilities.setStyleClass(this.deviceListEntry, "status-free", false);
        UiUtilities.setStyleClass(this.deviceListEntry, "status-disabled", false);
        UiUtilities.setStyleClass(this.deviceListEntry, "status-door-opened", false);
        UiUtilities.setStyleClass(this.deviceListEntry, "status-occupied", false);
        UiUtilities.setStyleClass(this.deviceListEntry, "status-unregistered", false);
        UiUtilities.setStyleClass(this.deviceListEntry, "status-error", false);
        UiUtilities.setStyleClass(this.deviceListEntry, "locked", false);
    }

    /**
     * Wechselt den Zustand der Gerätekachel zur Anzeige eines Fehlers
     *
     * @param message          Die anzuzeigende Fehlermeldung
     * @param exception        Der Fehler, der auf der Detail-Seite angezeigt werden soll
     * @param errorRetryAction Die Aktion, die die fehlgeschlagene Aktion wiederholt
     */
    private void displayError(String message, Exception exception, Runnable errorRetryAction) {
        synchronized (this.lock) {
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
        synchronized (this.lock) {
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
        synchronized (this.lock) {
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
        this.registrationScan.start(ElwaManager.instance.getDeviceRegistrationService(), this.device,
                active -> UiUtilities.setStyleClass(this.registerButton, "active", active),
                () -> this.refresh(true));
    }

    public void setDevice(ClientDevice device) {
        this.device = device;
    }

    // Property-Accessoren für die FXML-Bindings ${controller.…} in DeviceListEntry.fxml.
    // Sie bleiben bewusst hier (statt in einem eigenen Modell-Objekt): JavaFX' BeanAdapter löst
    // ${controller.x} genau über getX()/xProperty() AM CONTROLLER auf. Die sieben ungenutzten
    // Setter sind entfallen (#91) - die Werte werden ausschließlich intern über die Properties
    // gesetzt.

    /**
     * Property: deviceName
     */
    public String getDeviceName() {
        return deviceName.get();
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

    public StringProperty statusTextProperty() {
        return statusText;
    }


    /**
     * Property: LastUserName
     */
    public String getLastUserName() {
        return lastUserName.get();
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

    public StringProperty remainingTimeProperty() {
        return remainingTime;
    }


    /**
     * Property: End Date
     */
    public String getEndDate() {
        return endDate.get();
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

    public StringProperty errorTextProperty() {
        return errorText;
    }

    /**
     * Property: Disabled Text
     */
    public String getDisabledText() {
        return disabledText.get();
    }

    public StringProperty disabledTextProperty() {
        return disabledText;
    }

    /**
     * Das Aussehen der Kachel in einem Zustand - eine Zeile der Zustandstabelle
     * {@link #APPEARANCES}. {@code null} bei den drei {@code Boolean}-Feldern heißt
     * "unverändert lassen" (der Zustand macht zu dieser Schaltfläche/Kachel keine Aussage).
     *
     * @param resetAllStyleClasses   Ob zuerst der komplette Klassensatz zurückgesetzt wird (#82).
     * @param stylesOff              Gezielt zu entfernende Style-Klassen (vor {@code stylesOn}).
     * @param stylesOn               Zu setzende Style-Klassen.
     * @param entryDisabled          Ob die gesamte Kachel gesperrt wird.
     * @param doorOpenButtonDisabled Ob "Tür freigeben" gesperrt wird.
     * @param selectButtonDisabled   Ob "Gerät buchen" gesperrt wird.
     * @param statusText             Der anzuzeigende Statustext.
     */
    private record EntryAppearance(boolean resetAllStyleClasses, List<String> stylesOff, List<String> stylesOn,
                                   Boolean entryDisabled, Boolean doorOpenButtonDisabled,
                                   Boolean selectButtonDisabled, String statusText) {
    }

    /**
     * Die Darstellung je Zustand als Tabelle statt als Anweisungsfolge je {@code switch}-Zweig
     * (#91). Damit ist auf einen Blick vergleichbar, welcher Zustand welche Style-Klassen und
     * Sperren setzt - genau die Eigenschaft, deren Fehlen den Fall-Through-Fehler #82 (DISABLED
     * fiel in UNREGISTERED durch) so lange unentdeckt ließ.
     */
    private static final Map<DeviceListEntryState, EntryAppearance> APPEARANCES =
            new EnumMap<>(Map.of(
                    DeviceListEntryState.FREE, new EntryAppearance(true, List.of(), List.of("status-free"),
                            false, false, true, "frei"),
                    DeviceListEntryState.FREE_AVAILABLE, new EntryAppearance(true, List.of(), List.of("status-free"),
                            false, false, false, "frei"),
                    DeviceListEntryState.FREE_BLOCKED, new EntryAppearance(true, List.of(),
                            List.of("status-free", "locked"), true, null, true, "nicht verfügbar"),
                    DeviceListEntryState.DOOR_OPENED, new EntryAppearance(false, List.of("status-free"),
                            List.of("status-door-opened"), null, null, true, "Tür freigegeben"),
                    DeviceListEntryState.OCCUPIED, new EntryAppearance(false, List.of("status-free"),
                            List.of("status-occupied"), null, null, null, "belegt"),
                    DeviceListEntryState.ERROR, new EntryAppearance(false,
                            List.of("status-occupied", "status-free", "status-door-opened"),
                            List.of("status-error"), null, null, null, "FEHLER"),
                    DeviceListEntryState.DISABLED, new EntryAppearance(true, List.of(),
                            List.of("status-disabled"), true, null, null, "deaktiviert"),
                    DeviceListEntryState.UNREGISTERED, new EntryAppearance(true, List.of(),
                            List.of("status-unregistered"), false, null, null, "Keine Steckdose")));

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
