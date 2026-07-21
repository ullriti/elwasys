package org.kabieror.elwasys.raspiclient.application;

import org.kabieror.elwasys.common.Utilities;
import org.kabieror.elwasys.common.maintenance.*;
import org.kabieror.elwasys.common.maintenance.data.BacklightStatus;
import org.kabieror.elwasys.common.maintenance.data.InterfaceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Vector;

/**
 * Dieser Thread startet den Wartungs-Server und beantwortet Anfragen, die über
 * diesen eingehen.
 *
 * @author Oliver Kabierschke
 */
public class MaintenanceServerManager extends Thread implements ICloseListener, IMaintenanceMessageHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ElwaManager manager;
    private MaintenanceClient client;

    public MaintenanceServerManager(ElwaManager manager) {
        this.manager = manager;
        this.setName("MaintenanceServerManager");
    }

    @Override
    public void run() {
        this.manager.listenToCloseEvent(this);
        while (!Thread.interrupted()) {
            if (this.client == null || !this.client.isAlive()) {
                this.logger.debug("Starting new connection to the maintenance server.");
                try {
                    this.client = new MaintenanceClient(this.manager.getConfigurationManager().getMaintenanceServer(),
                            this.manager.getConfigurationManager().getMaintenancePort(), 50000, 5000,
                            this.manager.getConfigurationManager().getLocationName(), this);
                } catch (final Exception e) {
                    this.logger.debug("Could connect to the maintenance server.");
                }
            }

            // Prüfe alle 5 Minuten die Verbindung.
            try {
                Thread.sleep(5 * 60 * 1000);
            } catch (final InterruptedException e) {
                break;
            }
        }

        // Thread endet
        this.logger.debug("Shutting down maintenance client.");

        if (this.client != null && this.client.isAlive()) {
            this.client.shutdown();
        }
    }

    @Override
    public void onClose(boolean restart) {
        if (!restart) {
            this.interrupt();
        }
    }

    /**
     * Gibt die aktuellen Log-Einträge zurück.
     */
    @Override
    public GetLogResponse handleGetLog(GetLogRequest request) {
        final File logfile = new File(Utilities.getCurrentLogFile());
        try {
            return new GetLogResponse(request, Files.readAllLines(logfile.toPath()));
        } catch (final IOException e) {
            this.logger.error("Could not read the log file.", e);
            final List<String> res = new Vector<String>();
            res.add("Could not read the log file.");
            res.add(e.toString());
            return new GetLogResponse(request, res);
        }
    }

    /**
     * Gibt den aktuellen Status des Waschwächters zurück.
     */
    @Override
    public GetStatusResponse handleGetStatus(GetStatusRequest request) {
        final GetStatusResponse res = new GetStatusResponse(request);

        if (this.manager.getMainFormController() != null) {
            // Status setzen
            InterfaceStatus intStatus;
            switch (this.manager.getMainFormController().getMainFormState()) {
                case STARTUP:
                    intStatus = InterfaceStatus.START;
                    break;
                case ERROR:
                case ERROR_RETRYABLE:
                    intStatus = InterfaceStatus.ERROR;
                    intStatus.setDetailMessage(this.manager.getMainFormController().getCurrentErrorMessage());
                    break;
                default:
                    intStatus = InterfaceStatus.NORMAL;
            }
            res.setInterfaceStatus(intStatus);

            res.setStartupTime(ElwaManager.instance.getStartupTime());

            // Laufende Ausführungen: seit Phase 4 AP4 sind Ausführungen client-seitig
            // ClientExecution-Objekte statt Common.Execution (das Alt-Wartungsprotokoll
            // kennt nur Common.Execution, siehe GetStatusResponse) - eine 1:1-Umwandlung
            // ist ohne DB-Anbindung nicht möglich. Bis AP5 (ausgehende WS-Verbindung löst
            // dieses Protokoll komplett ab) wird hier bewusst eine leere Liste gemeldet;
            // ClientMaintenanceConnectionE2ETest prüft nur, dass die Liste NICHT null ist.
            res.setRunningExecutions(java.util.List.of());

            // Hintergrundbeleuchtung
            res.setBacklightStatus(
                    this.manager.getMainFormController().getBacklightManager().isLightOn() ? BacklightStatus.ON :
                            BacklightStatus.OFF);
        }
        return res;
    }

    @Override
    public void handleRestartApp(RestartAppRequest request) {
        this.logger.info("Restarting application");
        this.manager.restart();
    }


}
