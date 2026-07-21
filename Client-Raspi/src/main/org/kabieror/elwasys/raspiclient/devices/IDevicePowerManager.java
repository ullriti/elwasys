package org.kabieror.elwasys.raspiclient.devices;

import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.executions.FhemException;

import java.io.IOException;

public interface IDevicePowerManager {

    void addPowerMeasurementListener(IDevicePowerMeasurementHandler handler);

    /**
     * Switches the power of a device on.
     *
     * @param device The device to switch on.
     * @throws InterruptedException
     */
    void setDevicePowerState(ClientDevice device, DevicePowerState newState)
            throws IOException, InterruptedException, FhemException;

    /**
     * Looks up the power state of a device.
     *
     * @param device Der Gerät, dessen Status geholt werden soll.
     * @return The power state of the device.
     */
    DevicePowerState getState(ClientDevice device) throws InterruptedException, FhemException, IOException;


}

