package org.kabieror.elwasys.raspiclient.devices.deconz;

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
        deconzService.setDeviceState(device.getDeconzUuid(),
                newState == DevicePowerState.SET_ON || newState == DevicePowerState.ON);
    }

    @Override
    public DevicePowerState getState(ClientDevice device) throws InterruptedException, FhemException, IOException {
        if (device.getDeconzUuid() == null) {
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
