package org.kabieror.elwasys.raspiclient.application;

import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.commons.lang3.StringUtils;
import org.kabieror.elwasys.common.LocationOccupiedException;
import org.kabieror.elwasys.common.NoDataFoundException;
import org.kabieror.elwasys.common.Utilities;
import org.kabieror.elwasys.raspiclient.api.ApiClient;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceOverviewDto;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionDto;
import org.kabieror.elwasys.raspiclient.configuration.WashguardConfiguration;
import org.kabieror.elwasys.raspiclient.devices.FhemDevicePowerManager;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerManager;
import org.kabieror.elwasys.raspiclient.devices.IDeviceRegistrationService;
import org.kabieror.elwasys.raspiclient.devices.deconz.DeconzApiAdapter;
import org.kabieror.elwasys.raspiclient.devices.deconz.DeconzDevicePowerManager;
import org.kabieror.elwasys.raspiclient.devices.deconz.DeconzEventListener;
import org.kabieror.elwasys.raspiclient.devices.deconz.DeconzRegistrationService;
import org.kabieror.elwasys.raspiclient.executions.ExecutionManager;
import org.kabieror.elwasys.raspiclient.executions.FhemException;
import org.kabieror.elwasys.raspiclient.io.CardReader;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.model.ClientUser;
import org.kabieror.elwasys.raspiclient.ui.AbstractMainFormController;
import org.kabieror.elwasys.raspiclient.ws.TerminalWebSocketClient;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class ElwaManager {

    public static final String APP_NAME = "elwaClient";
    public static final String VERSION = Utilities.APP_VERSION;
    public final static ElwaManager instance = new ElwaManager();

    /**
     * Logger
     */
    private final org.slf4j.Logger logger;

    /**
     * Die Utilities-Instanz.
     */
    private final Utilities utilities;

    /**
     * Listener
     */
    private final List<ICloseRequestListener> closeRequestListeners;
    private final List<ICloseListener> closeListeners;

    /**
     * Identitäts-Cache der von diesem Client verwalteten Geräte, je Geräte-Id
     * (Phase 4 AP4). Bildet den Identitäts-Cache nach, den {@code Common.DataManager}
     * intern führte (siehe {@link ClientDevice} Klassenkommentar) - wichtig, damit
     * {@link ClientDevice#getCurrentExecution()} über mehrere
     * {@link #getManagedDevices()}-Aufrufe hinweg konsistent bleibt.
     */
    private final Map<Integer, ClientDevice> deviceCache = new ConcurrentHashMap<>();

    /**
     * Die Anbindung an das Backend über die REST-API v1 (Phase 4 AP4). Ersetzt
     * {@link #dataManager} als primären Datenzugriffspfad des Terminals.
     */
    private ApiClient apiClient;

    /**
     * Die ausgehende WebSocket-Verbindung zum Backend (Phase 4 AP5, siehe
     * kb/05-migration-plan.md "Arbeitspakete Phase 4", AP5-Auftrag). Ersetzt die ehemalige
     * Fernwartungs-Registrierung über {@code LocationManager}/{@code MaintenanceServerManager}
     * (Direkt-DB-Zugriff, Terminal lauschte als TCP-Server) - damit ist der letzte
     * Direkt-DB-Zugriff des Terminals entfallen, siehe {@link TerminalWebSocketClient}.
     */
    private TerminalWebSocketClient terminalWebSocketClient;

    /**
     * Der Manager für die Konfiguration
     */
    private WashguardConfiguration configurationManager;

    /**
     * Der Manager für Programmausführungen
     */
    private ExecutionManager executionManager;

    /**
     * Der Manager für das freigeben und abschalten des Stroms von verwalteten
     * Geräten
     */
    private IDevicePowerManager devicePowerManager;

    private IDeviceRegistrationService deviceRegistrationService;

    /**
     * Der Controller für das Hauptformular
     */
    private AbstractMainFormController mainFormController;

    /**
     * Der Kartenleser
     */
    private CardReader cardReader;

    /**
     * Das Hauptfenster
     */
    private Stage primaryStage;

    private LocalDateTime startupTime = LocalDateTime.now();

    private ElwaManager() {
        this.logger = LoggerFactory.getLogger(this.getClass());
        this.logger.info("----------------------------------------------------------------");
        this.logger.info("elwaClient " + VERSION);
        this.logger.info("Operating System: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        this.logger.info("Java Runtime Environment: " + System.getProperty("java.version"));
        final Runtime rtime = Runtime.getRuntime();
        this.logger.info("Processors: " + rtime.availableProcessors());
        this.logger.info("Memory: " + rtime.totalMemory());
        this.logger.info("Working directory: " + System.getProperty("user.dir"));
        this.logger.info("----------------------------------------------------------------");

        this.logger.info("Client is starting up");

        this.closeListeners = new Vector<ICloseListener>();
        this.closeRequestListeners = new Vector<ICloseRequestListener>();
        try {
            this.configurationManager = new WashguardConfiguration();
        } catch (final Exception e) {
            this.logger.error("Could not load configuration. Terminating.");
            System.exit(1);
        }
        this.utilities = new Utilities(this.configurationManager);
    }

    /**
     * Initiiert die Manager
     */
    public void initiate()
            throws ClassNotFoundException, SQLException, IOException, InterruptedException,
            LocationOccupiedException, FhemException, NoDataFoundException, AlreadyRunningException {
        DeconzEventListener deconzEventListener = null;
        try {
            this.logger.info("Starting up managers");
            SingleInstanceManager.instance.start(this.configurationManager.getSingleInstancePort());

            this.apiClient = new ApiClient(this.configurationManager.getBackendUrl(),
                    this.configurationManager.getBackendToken());

            // Ausgehende Fernwartungs-Verbindung (Phase 4 AP5, siehe kb/05-migration-plan.md
            // "Arbeitspakete Phase 4", AP5): ersetzt die ehemalige, Direkt-DB-basierte
            // Registrierung (LocationManager/MaintenanceServerManager). Läuft mit derselben
            // backend.url/backend.token-Konfiguration wie die REST-API (kein neuer Konfig-
            // Schlüssel nötig) und überlebt einen späteren restart() (siehe
            // TerminalWebSocketClient#onClose).
            this.terminalWebSocketClient = new TerminalWebSocketClient(this,
                    this.configurationManager.getBackendUrl(), this.configurationManager.getBackendToken(),
                    this.configurationManager.getUid());
            this.listenToCloseEvent(this.terminalWebSocketClient);
            this.terminalWebSocketClient.start();

            // Erreichbarkeits-Check des Backends beim Start (entspricht dem alten
            // "Standort aus der Datenbank laden"-Schritt, jetzt über die API - ein
            // nicht erreichbares Backend führt wie zuvor eine nicht erreichbare
            // Datenbank zum ERROR-Zustand, siehe Testfall C15).
            this.apiClient.getMyLocation();

            if (StringUtils.isNotBlank(this.configurationManager.getDeconzServer())) {
                this.logger.info("Using deCONZ as gateway.");
                var apiAdapter = new DeconzApiAdapter(this.configurationManager);
                deconzEventListener = new DeconzEventListener(this.configurationManager, apiAdapter);
                deconzEventListener.start();
                deviceRegistrationService = new DeconzRegistrationService(apiAdapter, deconzEventListener);
                this.devicePowerManager = new DeconzDevicePowerManager(apiAdapter, deconzEventListener);
            } else if (StringUtils.isNotBlank(this.configurationManager.getFhemConnectionString())) {
                this.logger.info("Using fhem as gateway.");
                this.devicePowerManager = new FhemDevicePowerManager(this.configurationManager);
            } else {
                this.logger.error("Application configuration is invalid. Could not find any device power gateway to use. " +
                        "You must either provide a value for deconz.server or fhem.server");
                System.exit(1);
            }
            this.executionManager = new ExecutionManager(this.devicePowerManager);
            this.mainFormController.initiate();

            // Setze unterbrochene Ausführungen fort (Testfall C13). Anders als der
            // Alt-Client (der ALLE Geräte systemweit über alle Standorte hinweg
            // scannte, siehe DataManager#getDevices()) ist dieser Scan jetzt korrekt
            // auf den eigenen Standort beschränkt (kommt implizit aus dem
            // Standort-Token) - siehe kb/05-migration-plan.md, Änderungslog "Phase 4
            // AP4" für die Einordnung dieses Befunds.
            for (ClientDevice d : this.getManagedDevices()) {
                DeviceOverviewDto overview = this.lastOverviewFor(d.getId());
                if (overview != null && overview.runningExecutionId() != null) {
                    ExecutionDto execDto = this.apiClient.getExecution(overview.runningExecutionId());
                    ClientProgram program = findProgram(d, execDto.programId());
                    ClientUser user = ClientUser.display(overview.lastUserId(), overview.lastUserName());
                    ClientExecution execution = ClientExecution.of(execDto, d, program, user);
                    d.onExecutionStarted(execution);
                    this.executionManager.startExecution(execution);
                }
            }
        } catch (Exception e) {
            this.logger.error("Failed to start up managers.", e);
            if (deconzEventListener != null) {
                deconzEventListener.stop();
            }
            throw e;
        }
    }

    private static ClientProgram findProgram(ClientDevice device, int programId) {
        for (ClientProgram p : device.getPrograms()) {
            if (p.getId() == programId) {
                return p;
            }
        }
        return null;
    }

    /**
     * Merkt sich den zuletzt geladenen Übersichts-Datensatz je Gerät, damit
     * {@link #initiate()} nach {@link #getManagedDevices()} nicht dieselben Daten ein
     * zweites Mal laden muss.
     */
    private final Map<Integer, DeviceOverviewDto> lastOverview = new ConcurrentHashMap<>();

    private DeviceOverviewDto lastOverviewFor(int deviceId) {
        return this.lastOverview.get(deviceId);
    }

    /**
     * Gibt den Konfigurationsmanager zurück
     *
     * @return Den Konfigurationsmanager
     */
    public WashguardConfiguration getConfigurationManager() {
        return this.configurationManager;
    }

    /**
     * Gibt den Controller des Hauptformulars zurück
     *
     * @return Den Controller des Haupformulars
     */
    public AbstractMainFormController getMainFormController() {
        return this.mainFormController;
    }

    /**
     * Setzt den Controller des Haupformulars
     *
     * @param c Der Controller des Haupformulars
     */
    public void setMainFormController(AbstractMainFormController c) {
        this.mainFormController = c;
    }

    /**
     * Gibt den Kartenleser zurück
     *
     * @return Den Kartenleser
     */
    public CardReader getCardReader() {
        return this.cardReader;
    }

    /**
     * Gibt die Anbindung an die Backend-API zurück (Phase 4 AP4).
     *
     * @return Die Anbindung an die Backend-API.
     */
    public ApiClient getApiClient() {
        return this.apiClient;
    }

    /**
     * Gibt den Ausführungsmanager zurück.
     *
     * @return Den Ausführungsmanager.
     */
    public ExecutionManager getExecutionManager() {
        return this.executionManager;
    }

    public IDeviceRegistrationService getDeviceRegistrationService() {
        return this.deviceRegistrationService;
    }

    /**
     * Gibt die Utilities-Instanz zurück.
     *
     * @return Die Utilities-Instanz.
     */
    public Utilities getUtilities() {
        return this.utilities;
    }

    /**
     * Gibt das Hauptfenster zurück
     *
     * @return Das Hauptfenster
     */
    public Stage getPrimaryStage() {
        return this.primaryStage;
    }

    /**
     * Registriert einen neuen Interessenten am Schließen-Event des Haupfensters
     *
     * @param l Der Interessent am Schließen-Event des Hauptfensters
     */
    public void listenToCloseRequest(ICloseRequestListener l) {
        this.closeRequestListeners.add(l);
    }

    /**
     * Registriert einen neuen Interessenten an Schließen-Anfragen des
     * Haupfensters
     *
     * @param l Der Interessent an Schließen-Anfragen des Haupfensters
     */
    public void listenToCloseEvent(ICloseListener l) {
        this.closeListeners.add(l);
    }

    /**
     * Wird aufgerufen, sobald ein Hauptfenster verfügbar ist
     *
     * @param primaryStage Das Hauptfenster
     */
    protected void onPrimaryStageStart(Stage primaryStage) {
        this.primaryStage = primaryStage;
        if (this.cardReader == null) {
            this.cardReader = new CardReader(primaryStage);
        }
    }

    /**
     * Eine Anfrage zum Schließen des Hauptfensters behandeln
     *
     * @param e Das Window-Event
     */
    protected void onCloseRequest(WindowEvent e) {
        this.logger.debug("Processing close request");
        for (final ICloseRequestListener l : this.closeRequestListeners) {
            l.onCloseRequest(e);
            if (e.isConsumed()) {
                // Breche Ausführung ab, wenn das Event abgefangen wurde und das
                // Schließen so verhindert wird
                return;
            }
        }

        // Close-Request wurde nicht abgebrochen. Informiere über das Beenden
        // der Anwendung.
        this.onClose(false);
    }

    /**
     * Wird aufgerufen, sobald die Anwendung beendet werden soll. Informiert
     * alle Manager über das Ende der Anwendung.
     */
    public void onClose(boolean restart) {
        this.logger.info("Application is terminating now.");
        for (final ICloseListener l : this.closeListeners) {
            l.onClose(restart);
        }
    }

    /**
     * Startet alle Manager neu.
     */
    public void restart() {
        this.onClose(true);
        this.mainFormController.initialize(null, null);
    }

    /**
     * Gibt alle Geräte zurück, die von diesem Client verwaltet werden sollen. Behält
     * dabei die Objekt-Identität je Geräte-Id über mehrere Aufrufe hinweg bei (siehe
     * {@link ClientDevice} Klassenkommentar).
     */
    public List<ClientDevice> getManagedDevices() throws ApiException {
        List<DeviceOverviewDto> overview = this.apiClient.getDevicesOverview();
        List<ClientDevice> result = new java.util.ArrayList<>(overview.size());
        for (DeviceOverviewDto dto : overview) {
            ClientDevice device = this.deviceCache.computeIfAbsent(dto.id(), ClientDevice::new);
            device.updateFrom(dto);
            this.lastOverview.put(dto.id(), dto);
            result.add(device);
        }
        return result;
    }

    /**
     * Die Zeit des Starts der Anwendung.
     *
     * @return
     */
    public LocalDateTime getStartupTime() {
        return startupTime;
    }
}
