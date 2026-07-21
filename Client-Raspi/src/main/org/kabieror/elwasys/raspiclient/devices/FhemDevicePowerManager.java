package org.kabieror.elwasys.raspiclient.devices;

import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.application.ICloseListener;
import org.kabieror.elwasys.raspiclient.application.Main;
import org.kabieror.elwasys.raspiclient.configuration.WashguardConfiguration;
import org.kabieror.elwasys.raspiclient.executions.FhemException;
import org.kabieror.elwasys.raspiclient.io.TelnetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dieser Manager schaltet den Strom von Geräten frei und unterbricht ihn.
 *
 * @author Oliver Kabierschke
 */
@SuppressWarnings("FieldCanBeLocal")
public class FhemDevicePowerManager implements IDevicePowerManager, ICloseListener {

    /**
     * Sperre für die gleichzeitige Ausführung von Telnet-Anfragen.
     */
    private final static Object TELNET_LOCK = new Object();
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * The pattern which indicates power events.
     */
    private final Pattern eventsPowerPattern = Pattern.compile("\\s([^\\s]+)\\spower:\\s(\\d+(\\.\\d+)?)");
    /**
     * Die Konfiguration des Programms
     */
    private final WashguardConfiguration config;
    /**
     * Process a full connection check at most once per 20 seconds.
     */
    private final Duration fullConnectionCheckDelay = Duration.ofSeconds(20);
    /**
     * The default time to wait for an answer from the fhem server in
     * milliseconds.
     */
    private final int defaultTimeout = 5000;
    /**
     * The delay between two status checks after the change of the device power
     * state.
     */
    private final int checkStatusDelay = 100;
    /**
     * The amount of status checks to perform before notifying the caller about
     * an unsuccessful action.
     */
    private final int checkStatusRetryCount = 50;
    /**
     * Der Telnet-Client, über welchen mit dem FHEM-Server kommuniziert werden
     * kann.
     */
    private TelnetClient telnetFhem;
    /**
     * Der Telnet-Client, über welchen events vom FHEM-Server empfangen werden.
     */
    private TelnetClient telnetFhemEvents;
    /**
     * Thread, welcher auf Events vom FHEM-Server wartet.
     */
    private Thread eventsReceiverThread;
    /**
     * Die Anzahl an Zeilen, welche beim Kommando "version" zu erwarten sind.
     * Wird bei der Verbindungsprüfung verwendet.
     */
    private int versionLinesToExpect = 0;
    /**
     * The date of the last full connection check.
     */
    private LocalDateTime lastFullConnectionCheck;
    /**
     * The minimum time to wait for an answer from the fhem server.
     */
    private Duration minimumTimeout = Duration.ZERO;

    private List<IDevicePowerMeasurementHandler> powerMeasurementHandlers = new LinkedList<>();

    public FhemDevicePowerManager(WashguardConfiguration config) throws InterruptedException, FhemException {
        this.config = config;
        if (!Main.dry) {
            this.openFhemConnection();
            this.openFhemEventsConnection(true);
            ElwaManager.instance.listenToCloseEvent(this);
        } else {
            this.logger
                    .warn("Starting in dry mode without setting power of physical devices. Remove the '-dry' argument" +
                            " to go to production mode.");
        }
    }

    @Override
    public void addPowerMeasurementListener(IDevicePowerMeasurementHandler handler) {
        this.powerMeasurementHandlers.add(handler);
    }

    /**
     * Switches the power of a device on.
     *
     * @param device The device to switch on.
     * @throws InterruptedException
     */
    @Override
    public void setDevicePowerState(ClientDevice device, DevicePowerState newState)
            throws IOException, InterruptedException, FhemException {
        synchronized (TELNET_LOCK) {
            if (newState != DevicePowerState.ON && newState != DevicePowerState.OFF) {
                throw new IllegalArgumentException("Der neue Zustand eines Geräts muss entweder ON oder OFF sein.");
            }
            if (Main.dry) {
                return;
            }
            if (!this.checkConnection()) {
                this.openFhemConnection();
            }

            // Setze Zustand
            this.telnetFhem.emptyResponseBuffer();
            String setCommand = "set " + device.getFhemSwitchName() + " " + newState.name().toLowerCase();
            this.telnetFhem.sendCommand(setCommand);

            // Check response from server. If it is empty, the command has been
            // executed.
            final String res = this.telnetFhem
                    .waitForResponse(this.minimumTimeout.multipliedBy(2).getNano(), TimeUnit.NANOSECONDS);
            if (res != null && !res.isEmpty()) {
                throw new IOException("Konnte die Stromversorgung des Geräts " + device.getName() +
                        " nicht setzen. Antwort des FHEM-Servers: '" + res + "'");
            } else {
                // Ensure that the action has been successful
                DevicePowerState actualState = DevicePowerState.UNKNOWN;
                for (int i = 0; i < this.checkStatusRetryCount; i++) {
                    // Repeat checking the state for a fixed count of repetitions
                    // before throwing an exception
                    try {
                        Thread.sleep(this.checkStatusDelay);
                    } catch (final InterruptedException e1) {
                        this.logger.warn("Interrupted while checking the power state.", e1);
                        throw new IOException("Unterbrechung während dem prüfen des neuen Zustands.", e1);
                    }

                    // Check the state
                    actualState = this.getState(device);
                    if (actualState == newState) {
                        break;
                    }
                    if (actualState == DevicePowerState.ON || actualState == DevicePowerState.OFF) {
                        // Server hat den Befehl nicht empfangen. Wiederhole ihn.
                        this.telnetFhem.sendCommand(setCommand);
                    }
                    // If the state is not the intended one, continue checking.
                }
                if (actualState != newState) {
                    // If the state is not the intended one, throw an exception.
                    String stateString;
                    try {
                        stateString = this.getRawState(device);
                        if (stateString == null || stateString.isEmpty()) {
                            stateString = "";
                        } else {
                            stateString = " Sein aktueller Zustand: " + stateString + ".";
                        }
                    } catch (final IOException e) {
                        stateString = "";
                    }
                    this.logger
                            .error("Could not set the power state of device " + device.getName() + "." + stateString);
                    throw new IOException(
                            "Konnte die Stromversorgung des Geräts " + device.getName() + " nicht setzen." +
                                    stateString);
                }
            }
        }
    }

    /**
     * Looks up the power state of a device.
     *
     * @param device Der Gerät, dessen Status geholt werden soll.
     * @return The power state of the device.
     */
    @Override
    public DevicePowerState getState(ClientDevice device) throws InterruptedException, FhemException, IOException {
        if (Main.dry) {
            return DevicePowerState.UNKNOWN;
        }

        synchronized (TELNET_LOCK) {
            if (!this.checkConnection()) {
                this.openFhemConnection();
            }
            this.telnetFhem.emptyResponseBuffer();
            this.telnetFhem.sendCommand("get " + device.getFhemSwitchName() + " param state");
            final String stateString = this.telnetFhem.waitForResponse(this.defaultTimeout, TimeUnit.MILLISECONDS);
            switch (stateString) {
                case "on":
                    return DevicePowerState.ON;
                case "off":
                    return DevicePowerState.OFF;
                case "set_on":
                    return DevicePowerState.SET_ON;
                case "set_off":
                    return DevicePowerState.SET_OFF;
                default:
                    return DevicePowerState.UNKNOWN;
            }
        }
    }

    /**
     * Closes the connection to the fhem server.
     */
    @Override
    public void onClose(boolean restart) {
        this.logger.debug("Shutting down DevicePowerManager");
        if (this.telnetFhem != null && this.telnetFhem.isAlive()) {
            this.telnetFhem.shutdown();
        }
        this.eventsReceiverThread.interrupt();
        if (this.telnetFhemEvents != null && this.telnetFhemEvents.isAlive()) {
            this.telnetFhemEvents.shutdown();
        }
    }

    /**
     * Startet eine neue Telnet-Sitzung mit dem Fhem-Server.
     */
    private void openFhemConnection() throws InterruptedException, FhemException {
        if (this.telnetFhem != null && this.telnetFhem.isAlive()) {
            this.telnetFhem.shutdown();
        }
        this.logger.info("Starting new connection to fhem server on " + this.config.getFhemConnectionString() + ":" +
                this.config.getFhemPort());
        this.telnetFhem = new TelnetClient(this.config.getFhemConnectionString(), this.config.getFhemPort());
        try {
            this.telnetFhem.openConnection(5000);
        } catch (IOException e) {
            throw new FhemException("Konnte nicht mit dem FHEM-Server verbinden.", e);
        }
        try {
            Thread.sleep(1000);
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        // Check connection
        try {
            this.telnetFhem.sendCommand("version");
            final LocalDateTime startWait = LocalDateTime.now();
            String response = this.telnetFhem.waitForResponse(5, TimeUnit.SECONDS);
            String lastResponse = response;
            int sumWaitDuration = 0;
            int countResponses = 0;
            while (lastResponse != null) {
                final Duration duration = Duration.between(startWait, LocalDateTime.now());
                sumWaitDuration += duration.getNano();
                if (this.minimumTimeout.minus(duration).isNegative()) {
                    // Update minimum wait duration, if the measured duration is
                    // greather.
                    this.minimumTimeout = duration;
                }
                countResponses++;

                // Wait for next response
                final int avgWaitDuration = sumWaitDuration / countResponses;
                lastResponse = this.telnetFhem.waitForResponse(avgWaitDuration * 10, TimeUnit.NANOSECONDS);
                if (lastResponse != null && !lastResponse.isEmpty()) {
                    response += "\n" + lastResponse;
                }
            }
            if (response == null || !response.contains("fhem.pl")) {
                // No response received.
                this.logger.error("The fhem server did not send a matching response. Expected 'fhem.pl' but got:\n" +
                        response);
                throw new FhemException("Der FHEM-Server hat unerwartet geantwortet.");
            }
            this.logger.trace("Expecting " + countResponses + " lines from the version command.");
            this.versionLinesToExpect = countResponses;
            this.lastFullConnectionCheck = LocalDateTime.now();
        } catch (IOException e) {
            throw new FhemException("Konnte nicht mit dem FHEM-Server kommunizieren.", e);
        }
    }

    /**
     * Startet eine neue Telnet-Sitzung mit dem FHEM-Server, auf welchem Events
     * empfangen werden.
     *
     * @throws FhemException
     */
    private void openFhemEventsConnection(boolean newReceiverThread) throws FhemException {
        if (this.telnetFhemEvents != null && this.telnetFhemEvents.isAlive()) {
            this.telnetFhemEvents.shutdown();
        }
        if (newReceiverThread && this.eventsReceiverThread != null && this.eventsReceiverThread.isAlive()) {
            this.eventsReceiverThread.interrupt();
        }

        try {
            this.logger
                    .info("Starting new events connection to fhem server on " + this.config.getFhemConnectionString() +
                            ":" + this.config.getFhemPort());
            this.telnetFhemEvents = new TelnetClient(this.config.getFhemConnectionString(), this.config.getFhemPort());
            this.telnetFhemEvents.openConnection(5000);

            if (!this.checkConnection(this.telnetFhemEvents, true)) {
                throw new IOException("The opened events connection to the fhem server is not valid.");
            }

            this.telnetFhemEvents.sendCommand("inform on");
        } catch (IOException e) {
            throw new FhemException("Konnte keine Events-Verbindung zum FHEM-Server aufbauen.", e);
        }

        if (newReceiverThread || this.eventsReceiverThread == null || !this.eventsReceiverThread.isAlive()) {
            this.eventsReceiverThread = new Thread(() -> {
                eventLoop:
                while (!Thread.interrupted()) {
                    // If no event is received for 5 minutes, open a new connection.
                    String event = null;
                    try {
                        event = this.telnetFhemEvents.waitForResponse(5, TimeUnit.MINUTES);
                    } catch (final IOException e) {
                        this.logger.error("Error while waiting for events.", e);
                    } catch (final InterruptedException e) {
                        // Terminate thread.
                        break;
                    }
                    if (event != null) {
                        this.onEventReceived(event);
                    } else {
                        this.logger.warn("Telnet session for receiving events is broken or dead. " +
                                "A new connection is being opened.");
                        while (!Thread.interrupted()) {
                            // Try opening a new event connection once per five
                            // seconds.
                            try {
                                this.openFhemEventsConnection(false);
                                break;
                            } catch (final FhemException e) {
                                this.logger.error("Could not open a new events connection.", e);
                                try {
                                    Thread.sleep(5000);
                                } catch (final InterruptedException e1) {
                                    break eventLoop;
                                }
                            }
                        }
                    }
                }
            });
            this.eventsReceiverThread.start();
        }
    }

    /**
     * Wir aufgerufen, sobald vom fhem Server ein Ereignis empfangen worden ist.
     *
     * @param event Das Ereignis.
     */
    private void onEventReceived(String event) {
        final Matcher powerMatcher = this.eventsPowerPattern.matcher(event);

        if (powerMatcher.find()) {
            for (final ClientExecution execution : ElwaManager.instance.getExecutionManager().getRunningExecutions()) {
                if (powerMatcher.group(1).equals(execution.getDevice().getFhemPowerName())) {
                    this.logger.trace("Power Measurement received: " + event);
                    Double measurement = Double.parseDouble(powerMatcher.group(2));
                    this.powerMeasurementHandlers.forEach(l -> {
                        l.onPowerMeasurementAvailable(execution, measurement);
                    });
                    return;
                }
            }
        } else {
            this.logger.info("Could not parse power measurement event: " + event);
        }
    }

    /**
     * Checks the connection to the Fhem-Server. If it is not alive, an
     * IOException is being thrown.
     *
     * @return True, if the connection is alive.
     */
    private boolean checkConnection(TelnetClient telnet, boolean forceFullCheck) {
        Boolean res = null;
        final LocalDateTime checkStart = LocalDateTime.now();
        try {
            if (telnet == null || !telnet.isAlive()) {
                return false;
            }
            if (!forceFullCheck && this.lastFullConnectionCheck != null &&
                    Duration.between(this.lastFullConnectionCheck, LocalDateTime.now())
                            .minus(this.fullConnectionCheckDelay).isNegative()) {
                this.logger.trace("Fast connection check returns true.");
                return true;
            }
            this.logger.trace("Full connection check started.");
            telnet.emptyResponseBuffer();
            telnet.sendCommand("version");
            this.logger.trace("Waiting for anwser");
            String answer = telnet.waitForResponse(5, TimeUnit.SECONDS);

            // Retrieve remaining response lines
            for (int i = 1; i < this.versionLinesToExpect; i++) {
                String newAnswer = telnet.waitForResponse(5, TimeUnit.SECONDS);
                answer += "\n" + newAnswer;
                if (newAnswer == null) {
                    // A response is missing.
                    this.logger
                            .warn("The server did not send a matching response. Expected " + this.versionLinesToExpect +
                                    " lines but got " + i + ".");
                    res = false;
                    break;
                }
            }
            if (res == null) {
                res = answer != null && answer.contains("fhem.pl");
            }
        } catch (final IOException | InterruptedException e) {
            this.logger.warn("Error while checking the connection.", e);
            res = false;
        }
        this.logger.trace("Connection check returns " + res + " after " +
                Duration.between(checkStart, LocalDateTime.now()).toMillis() + "ms");
        this.lastFullConnectionCheck = LocalDateTime.now();
        return res;
    }

    /**
     * Checks the connection to the Fhem-Server. If it is not alive, an
     * IOException is being thrown.
     *
     * @return True, if the connection is alive.
     */
    private boolean checkConnection() {
        return Main.dry || this.checkConnection(this.telnetFhem, false);
    }

    /**
     * Looks up the state of a device.
     *
     * @param device The device of which the state is to be looked up.
     * @return The state of the device.
     */
    private String getRawState(ClientDevice device) throws IOException, InterruptedException, FhemException {
        if (Main.dry) {
            return "unknown";
        }

        if (!this.checkConnection()) {
            this.openFhemConnection();
        }
        this.telnetFhem.emptyResponseBuffer();
        this.telnetFhem.sendCommand("get " + device.getFhemSwitchName() + " param state");
        return this.telnetFhem.waitForResponse(this.defaultTimeout, TimeUnit.MILLISECONDS);
    }

}
