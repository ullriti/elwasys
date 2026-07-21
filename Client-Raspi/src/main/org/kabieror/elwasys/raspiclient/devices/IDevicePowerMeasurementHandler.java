package org.kabieror.elwasys.raspiclient.devices;

import org.kabieror.elwasys.raspiclient.model.ClientExecution;

public interface IDevicePowerMeasurementHandler {
    void onPowerMeasurementAvailable(ClientExecution execution, double currentPowerConsumption);
}