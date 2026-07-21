package org.kabieror.elwasys.raspiclient.executions;

import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.devices.DevicePowerState;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerManager;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledFuture;

/**
 * Diese Klasse führt die bei der Beendigung einer Programmausführung notwendigen Operationen aus.
 * <p>
 * Seit Phase 4 AP4 (siehe kb/05-migration-plan.md "Benachrichtigungen") versendet dieser
 * Client selbst KEINE Benachrichtigungen mehr (E-Mail/Pushover/elwaApp-Push sind komplett
 * entfernt, inkl. des in Phase 4 AP2 migrierten, faktisch toten Ionic-Push-Zweigs) - das
 * Backend versendet die Benachrichtigung serverseitig beim API-gemeldeten Execution-Ende
 * ({@code NotificationService}, hinter {@code elwasys.notifications.enabled}), ausgelöst
 * durch genau denselben {@code finish}/{@code abort}-Aufruf, den diese Klasse jetzt statt
 * der früheren Direkt-DB-Schreibvorgänge (Execution-Ende + Guthaben-Abbuchung) durchführt.
 *
 * @author Oliver Kabierschke
 */
class ExecutionFinisher implements Runnable {
    /**
     *
     */
    private final ExecutionManager executionManager;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Object lock = new Object();

    private final ClientExecution e;

    private ScheduledFuture<?> future;

    private Boolean executed = false;

    private boolean aborted = false;

    private IDevicePowerManager devicePowerManager;

    ExecutionFinisher(ExecutionManager executionManager, ClientExecution e, IDevicePowerManager devicePowerManager) {
        this.executionManager = executionManager;
        this.e = e;
        this.devicePowerManager = devicePowerManager;
    }

    @Override
    public void run() {
        synchronized (this.lock) {
            synchronized (e.getDevice()) {
                if (this.executed) {
                    return;
                }
                try {
                    this.executeAction();
                    this.executed = true;
                } catch (final Exception e) {
                    this.logger.error("Execution finisher failed.", e);
                    for (final IExecutionErrorListener l : this.executionManager.errorListeners) {
                        l.onExecutionFailed(this.e, e);
                    }
                }
            }
        }
    }

    void abort() {
        this.aborted = true;
        this.run();
    }

    /**
     * Versucht das erneute Ausführen der Fertigstellung einer
     * Programmausführung
     */
    void retry() throws IOException, InterruptedException, FhemException {
        this.executeAction();
    }

    private void executeAction() throws IOException, InterruptedException, FhemException {
        this.logger.info("[" + this.e.getDevice().getName() + "] Stopping execution " + this.e.getId());
        this.logger.info("[" + this.e.getDevice().getName() + "] User: " + this.e.getUser().getName());
        this.logger.info("[" + this.e.getDevice().getName() + "] Total time: " + this.e.getElapsedTimeString());

        // Breche geplante Ausführung ab, falls nicht von dieser
        // gestartet
        if (this.future != null) {
            this.future.cancel(false);
        }

        // Breche geplante automatische Stops ab
        if (this.executionManager.plannedStops.containsKey(this.e)) {
            this.executionManager.plannedStops.get(this.e).cancel(false);
        }

        // Schalte den Strom der Maschine aus
        try {
            this.devicePowerManager.setDevicePowerState(this.e.getDevice(), DevicePowerState.OFF);
        } catch (final IOException | InterruptedException | FhemException e1) {
            this.logger.error("[" + this.e.getDevice().getName() + "] Could not power off the device.", e1);
            throw e1;
        }

        // Informiere Ausführung über dessen Ende und veranlasse die Abrechnung. Für reale
        // Ausführungen läuft das jetzt über einen einzigen API-Aufruf (Backend erledigt
        // Ende + Guthaben-Abbuchung atomar, siehe ExecutionController#finish/abort); für
        // virtuelle/offline Ausführungen (Tür öffnen) bleibt es - wie im Alt-Code - rein
        // lokal und ohne Abrechnung.
        //
        // Phase 4 AP6 (Offline-Robustheit, siehe kb/05-migration-plan.md "Konzeptskizze:
        // Offline-Buchungen am Terminal"): eine WÄHREND eines Backend-Ausfalls offline
        // gebuchte Ausführung (e.isOfflinePendingReplay()) hat noch keine echte Backend-Id -
        // ihr Ende/Abbruch wird darum IMMER direkt im Ereignis-Journal hinterlegt, nie über
        // einen Live-Aufruf versucht (siehe ClientExecution Klassenkommentar). Für eine
        // normal online gestartete, reale Ausführung wird der Live-Aufruf wie bisher
        // versucht - scheitert er an einem reinen Kommunikationsfehler (Backend während der
        // laufenden Ausführung ausgefallen, Stufe A), wird das Ende ebenfalls lokal
        // vollzogen und nachgemeldet, statt den Bediener mit einem Fehler-/Retry-Zustand zu
        // konfrontieren ("kein Datenverlust bei Backend-Schluckauf" - siehe Auftrag). Ein
        // ECHTER fachlicher Fehler (z. B. 409 execution-already-finished) wird weiterhin wie
        // bisher als Fehler gemeldet (bestehendes Retry-UX unverändert).
        if (this.e.isVirtual()) {
            this.e.stopLocally();
        } else if (this.e.isOfflinePendingReplay()) {
            LocalDateTime clientTimestamp = LocalDateTime.now();
            String idempotencyKey = java.util.UUID.randomUUID().toString();
            ElwaManager.instance.getOfflineGateway()
                    .appendFinishOrAbort(this.e, this.aborted, clientTimestamp, idempotencyKey);
            this.e.stopLocally();
        } else {
            LocalDateTime clientTimestamp = LocalDateTime.now();
            String idempotencyKey = java.util.UUID.randomUUID().toString();
            try {
                var updated = this.aborted ? ElwaManager.instance.getApiClient()
                        .abortExecution(this.e.getId(), clientTimestamp, idempotencyKey)
                        : ElwaManager.instance.getApiClient()
                                .finishExecution(this.e.getId(), clientTimestamp, idempotencyKey);
                this.e.applyDto(updated);
            } catch (final ApiException e1) {
                if (e1.isCommunicationFailure()) {
                    this.logger.warn("[" + this.e.getDevice().getName() + "] Backend nicht erreichbar beim "
                            + "Beenden/Abbrechen - schliesse die Ausfuehrung lokal ab und melde sie nach der "
                            + "Wiederverbindung nach (Phase 4 AP6).", e1);
                    ElwaManager.instance.getOfflineGateway()
                            .appendFinishOrAbort(this.e, this.aborted, clientTimestamp, idempotencyKey);
                    this.e.stopLocally();
                } else {
                    this.logger.error(
                            "[" + this.e.getDevice().getName() + "] Could not finish the execution on the " +
                                    "backend.", e1);
                    throw e1;
                }
            }
        }

        // Informiere Gerät über Ende der Ausführung
        this.e.getDevice().onExecutionEnded();

        // Ausführung aus der Liste entfernen
        if (this.executionManager.executionFinishers.containsKey(this.e)) {
            this.executionManager.executionFinishers.remove(this.e);
        }

        if (this.executionManager.plannedStops.containsKey(this.e)) {
            this.executionManager.plannedStops.remove(this.e);
        }

        // Informiere alle Listener über das Ende der Programmausfürung
        for (final IExecutionFinishedListener l : this.executionManager.finishListeners) {
            l.onExecutionFinished(this.e);
        }
    }

    void setScheduledFuture(ScheduledFuture<?> future) {
        this.future = future;
    }
}
