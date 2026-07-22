package org.kabieror.elwasys.raspiclient.executions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.devices.DevicePowerState;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerManager;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerMeasurementHandler;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientUser;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressionstest zu Issue #28: {@link ExecutionFinisher#retry()} lief früher OHNE Lock und
 * ohne die {@code executed}-Prüfung von {@link ExecutionFinisher#run()} und konnte damit das
 * Finish ein ZWEITES Mal ausführen (zweiter Abrechnungs-/Abschalt-Vorgang, im Live-Fall ein
 * zweiter {@code finishExecution}-Aufruf mit neuem Idempotenz-Key → 409 → Fehleranzeige).
 * <p>
 * Deterministisch (kein Sleep/Zufall) über eine VIRTUELLE Ausführung ("Tür öffnen"), deren
 * Beenden rein lokal läuft und darum keine Backend-/OfflineGateway-Aufrufe braucht: nach einem
 * erfolgreichen {@link ExecutionFinisher#run()} darf ein anschließendes {@link
 * ExecutionFinisher#retry()} die Fertigstellung nicht erneut ausführen. Ohne den Fix schaltet
 * der Retry die Steckdose ein zweites Mal ab und feuert das Finish-Event doppelt.
 */
class ExecutionFinisherRetryTest {

    private ExecutionManager executionManager;

    @AfterEach
    void tearDown() {
        if (this.executionManager != null) {
            // Scheduler-Pool (inkl. 20s-Watchdog) sauber beenden, keine Threads lecken.
            this.executionManager.onClose(false);
        }
    }

    @Test
    void retry_after_a_successful_run_does_not_finish_a_second_time() throws Exception {
        final AtomicInteger powerOffCount = new AtomicInteger();
        final IDevicePowerManager powerManager = new CountingPowerManager(powerOffCount);
        this.executionManager = new ExecutionManager(powerManager);

        final AtomicInteger finishedEventCount = new AtomicInteger();
        this.executionManager.finishListeners.add(e -> finishedEventCount.incrementAndGet());

        final ClientDevice device = new ClientDevice(1);
        final ClientUser user = ClientUser.display(1, "Tester");
        // Virtuelle Ausführung: Beenden ist rein lokal (kein ApiClient/OfflineGateway nötig).
        final ClientExecution execution = ClientExecution.offline(device, null, user);
        device.onExecutionStarted(execution);

        final ExecutionFinisher finisher = new ExecutionFinisher(this.executionManager, execution, powerManager);

        // Erste, reguläre Fertigstellung.
        finisher.run();
        assertEquals(1, powerOffCount.get(), "run() sollte die Steckdose genau einmal abschalten");
        assertEquals(1, finishedEventCount.get(), "run() sollte das Finish-Event genau einmal feuern");

        // Zweiter Versuch über retry() - muss durch die executed-Prüfung ein No-Op sein.
        finisher.retry();
        assertEquals(1, powerOffCount.get(),
                "retry() nach erfolgreichem run() darf die Steckdose NICHT erneut abschalten (Doppel-Finish)");
        assertEquals(1, finishedEventCount.get(),
                "retry() nach erfolgreichem run() darf das Finish-Event NICHT erneut feuern");
    }

    /**
     * Minimaler {@link IDevicePowerManager} für den Test: zählt die {@code OFF}-Schaltvorgänge,
     * ohne echte Hardware/Netzwerk.
     */
    private static final class CountingPowerManager implements IDevicePowerManager {
        private final AtomicInteger powerOffCount;

        CountingPowerManager(AtomicInteger powerOffCount) {
            this.powerOffCount = powerOffCount;
        }

        @Override
        public void addPowerMeasurementListener(IDevicePowerMeasurementHandler handler) {
            // Für diesen Test irrelevant.
        }

        @Override
        public void setDevicePowerState(ClientDevice device, DevicePowerState newState) {
            if (newState == DevicePowerState.OFF) {
                this.powerOffCount.incrementAndGet();
            }
        }

        @Override
        public DevicePowerState getState(ClientDevice device) {
            return DevicePowerState.OFF;
        }
    }
}
