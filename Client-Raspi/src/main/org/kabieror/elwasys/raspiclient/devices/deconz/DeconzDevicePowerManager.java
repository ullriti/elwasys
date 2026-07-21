package org.kabieror.elwasys.raspiclient.devices.deconz;

import org.apache.commons.lang3.StringUtils;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.devices.DevicePowerState;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerManager;
import org.kabieror.elwasys.raspiclient.devices.IDevicePowerMeasurementHandler;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzEvent;
import org.kabieror.elwasys.raspiclient.executions.FhemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeconzDevicePowerManager implements IDevicePowerManager {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final DeconzEventListener eventListener;
    private final DeconzService deconzService;
    private final List<IDevicePowerMeasurementHandler> powerMeasurementListeners = new ArrayList<>();

    public DeconzDevicePowerManager(DeconzApiAdapter apiAdapter, DeconzEventListener eventListener) throws IOException, InterruptedException {
        this.eventListener = eventListener;
        ElwaManager.instance.listenToCloseEvent(restart -> onClosing());

        eventListener.listenToPowerMeasurementReceived(this::onPowerMeasurementReceived);

        deconzService = new DeconzService(apiAdapter, eventListener);
    }

    private void onPowerMeasurementReceived(DeconzEvent e) {
        this.logger.debug("Received: " + e.toString());

        var execution = ElwaManager.instance.getExecutionManager()
                .getRunningExecutions().stream()
                .filter(exe -> e.uniqueid().startsWith(exe.getDevice().getDeconzUuid()))
                .findFirst();
        execution.ifPresent(value ->
                this.powerMeasurementListeners.forEach(l -> l.onPowerMeasurementAvailable(value, e.state().power()))
        );
    }

    private void onClosing() {
        eventListener.stop();
    }

    @Override
    public void setDevicePowerState(ClientDevice device, DevicePowerState newState)
            throws IOException, InterruptedException, FhemException {
        // Verteidigung gegen ein Gerät ohne (oder mit leerer) deCONZ-Id (Phase 4
        // CI-Stabilität, siehe kb/05-migration-plan.md, Änderungslog "Phase 4
        // CI-Stabilität (deCONZ)"): ohne diese Prüfung würde ein solches Gerät hier
        // gegen einen aus einer leeren Id gebildeten (und damit auf keinen deCONZ-
        // Endpunkt routenden) Pfad laufen - eine wenig aussagekräftige Fehlerkette
        // ("Kommunikationsfehler" nach mehreren sinnlosen Wiederholungen) statt eines
        // klaren Fehlers. {@link #getState} prüfte bereits auf {@code null}, aber nicht
        // auf einen leeren String (die DB speichert "kein deCONZ-Gerät" als leeren
        // String, nicht NULL) - dieselbe {@code isNotBlank}-Prüfung wie in
        // {@link DeconzRegistrationService#isDeviceRegistered} deckt beide Fälle ab.
        if (StringUtils.isBlank(device.getDeconzUuid())) {
            throw new DeconzException(
                    "Gerät %s ist nicht bei deCONZ registriert (keine deCONZ-Id konfiguriert).".formatted(
                            device.getName()));
        }
        deconzService.setDeviceState(device.getDeconzUuid(),
                newState == DevicePowerState.SET_ON || newState == DevicePowerState.ON);
    }

    @Override
    public DevicePowerState getState(ClientDevice device) throws InterruptedException, FhemException, IOException {
        if (StringUtils.isBlank(device.getDeconzUuid())) {
            logger.warn("No deCONZ device registered for device %s".formatted(device.getId()));
            return DevicePowerState.UNKNOWN;
        }
        var isOn = deconzService.getDeviceState(device.getDeconzUuid()).on();
        return isOn ? DevicePowerState.ON : DevicePowerState.OFF;
    }

    @Override
    public void addPowerMeasurementListener(IDevicePowerMeasurementHandler handler) {
        this.powerMeasurementListeners.add(handler);
    }

}
