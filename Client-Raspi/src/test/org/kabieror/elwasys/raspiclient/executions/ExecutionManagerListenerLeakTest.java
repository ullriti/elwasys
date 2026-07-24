package org.kabieror.elwasys.raspiclient.executions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.devices.DevicePowerState;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerManager;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerMeasurementHandler;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.model.ClientUser;

/**
 * Regressionstest zu Issue #81 (H2): {@code stopListenToExecutionStartedEvent} rief statt
 * {@code startListeners.remove(l)} versehentlich {@code startListeners.add(l)} auf - ein
 * abgemeldeter Listener blieb also weiter registriert (Listener-Leak) und wurde bei jedem
 * weiteren Start einer Ausführung erneut benachrichtigt, statt gar nicht mehr. Derselbe
 * Copy-Paste-Fehler steckte unabhängig davon auch in {@code stopListenToExecutionErrorEvent}.
 * <p>
 * Deterministisch (kein Sleep/Zufall) über eine virtuelle "Tür öffnen"-Ausführung, deren Start
 * rein lokal läuft (kein ApiClient/OfflineGateway nötig) - siehe {@code
 * ExecutionFinisherRetryTest} für dasselbe Muster.
 */
class ExecutionManagerListenerLeakTest {

    private ExecutionManager executionManager;

    @AfterEach
    void tearDown() {
        if (this.executionManager != null) {
            // Scheduler-Pool (inkl. 20s-Watchdog) sauber beenden, keine Threads lecken.
            this.executionManager.onClose(false);
        }
    }

    @Test
    void aListenerThatUnregisteredIsNotNotifiedOnTheNextExecutionStart() throws Exception {
        final IDevicePowerManager powerManager = new NoopPowerManager();
        this.executionManager = new ExecutionManager(powerManager);

        final AtomicInteger startedEventCount = new AtomicInteger();
        final IExecutionStartedListener listener = e -> startedEventCount.incrementAndGet();

        this.executionManager.listenToExecutionStartedEvent(listener);
        this.executionManager.stopListenToExecutionStartedEvent(listener);

        final ClientDevice device = new ClientDevice(1);
        final ClientUser user = ClientUser.display(1, "Tester");
        // Virtuelle Ausführung ("Tür öffnen"): startExecution() bleibt rein lokal, löst also
        // keinen ApiClient-Aufruf aus (siehe ClientExecution-Klassenkommentar).
        final ClientExecution execution = ClientExecution.offline(device, ClientProgram.doorOpen(), user);

        this.executionManager.startExecution(execution);

        assertEquals(0, startedEventCount.get(),
                "a listener that unregistered before the broadcast must not be notified - "
                        + "without the fix, stopListenToExecutionStartedEvent re-adds the listener instead of "
                        + "removing it");
    }

    /**
     * Minimaler {@link IDevicePowerManager} für den Test: keine echte Hardware/Netzwerk, jede
     * Anfrage nach dem Leistungszustand meldet "aus".
     */
    private static final class NoopPowerManager implements IDevicePowerManager {

        @Override
        public void addPowerMeasurementListener(IDevicePowerMeasurementHandler handler) {
            // Für diesen Test irrelevant.
        }

        @Override
        public void setDevicePowerState(ClientDevice device, DevicePowerState newState) {
            // Für diesen Test irrelevant.
        }

        @Override
        public DevicePowerState getState(ClientDevice device) {
            return DevicePowerState.OFF;
        }
    }
}
