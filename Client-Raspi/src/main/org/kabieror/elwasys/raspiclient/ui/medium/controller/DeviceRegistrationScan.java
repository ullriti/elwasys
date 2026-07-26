package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import org.kabieror.elwasys.raspiclient.devices.IDeviceRegistrationService;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Der Suchlauf nach einer neuen Steckdose für eine Gerätekachel ("Suche nach Steckdose",
 * {@code DeviceListEntry#onRegister}). Aus {@link DeviceListEntry} herausgelöst (#91): die
 * Kachel führte dafür einen eigenen {@link ExecutorService}, der nirgends heruntergefahren
 * wurde - über wiederholte Restarts blieb je Kachel ein Thread zurück. Hier gehört der
 * Executor zum Objekt und wird von {@code DeviceListEntry#onTerminate()} über {@link #stop()}
 * mit beendet.
 * <p>
 * Der Suchlauf blockiert bewusst (er wartet über {@code join()} auf das Ende des
 * Pairing-Vorgangs am Gateway) und läuft darum nicht auf dem FX-Thread.
 */
class DeviceRegistrationScan {

    private final Logger logger = LoggerFactory.getLogger(DeviceRegistrationScan.class);

    private final ExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Startet einen Suchlauf für das gegebene Gerät.
     *
     * @param registrationService Der Registrierungsdienst des konfigurierten Gateways.
     * @param device              Das Gerät, für das eine Steckdose gesucht wird.
     * @param onScanStateChanged  Wird mit {@code true} beim Start und mit {@code false} am Ende
     *                            des Suchlaufs aufgerufen (Hervorhebung der Schaltfläche).
     * @param onFinished          Wird nach Abschluss des Suchlaufs aufgerufen (Kachel neu
     *                            zeichnen).
     */
    void start(IDeviceRegistrationService registrationService, ClientDevice device,
               java.util.function.Consumer<Boolean> onScanStateChanged, Runnable onFinished) {
        this.scheduler.submit(() -> {
            this.logger.info("Scanning for new actor for device {}", device.getId());
            onScanStateChanged.accept(true);
            registrationService.registerDevice(device).join();
            onScanStateChanged.accept(false);
            onFinished.run();
        });
    }

    /**
     * Beendet den Executor. Ein noch laufender Suchlauf wird dabei unterbrochen - beim
     * Herunterfahren/Neustart der Anwendung ist das genau das gewünschte Verhalten.
     */
    void stop() {
        this.scheduler.shutdownNow();
    }
}
