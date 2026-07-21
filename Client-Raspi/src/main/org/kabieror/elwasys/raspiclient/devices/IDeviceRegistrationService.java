package org.kabieror.elwasys.raspiclient.devices;

import org.kabieror.elwasys.raspiclient.model.ClientDevice;

import java.util.concurrent.CompletableFuture;

public interface IDeviceRegistrationService {
    /**
     * Checks whether a device can be controlled.
     */
    boolean isDeviceRegistered(ClientDevice device);

    /**
     * Tries to find a new remote socket for the given device.
     */
     CompletableFuture<Boolean> registerDevice(ClientDevice device);
}
