package org.kabieror.elwasys.raspiclient.devices.deconz;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.devices.IDeviceRegistrationService;
import org.kabieror.elwasys.raspiclient.devices.deconz.model.DeconzConfigPariing;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DeconzRegistrationService implements IDeviceRegistrationService {

    private static final int PAIRING_TIME_SECONDS = 30;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private CompletableFuture<String> currentRegistrationFuture;
    private final DeconzApiAdapter apiAdapter;
    private final Gson gson = new Gson();

    public DeconzRegistrationService(DeconzApiAdapter apiAdapter, DeconzEventListener eventListener) {
        this.apiAdapter = apiAdapter;
        eventListener.listenToDeviceRegisteredEvent(uuid -> {
            if (currentRegistrationFuture != null && !currentRegistrationFuture.isDone()) {
                logger.debug("Received registration event for device %s".formatted(uuid));
                currentRegistrationFuture.complete(uuid);
            }
        });
    }

    @Override
    public boolean isDeviceRegistered(ClientDevice device) {
        return StringUtils.isNotBlank(device.getDeconzUuid());
    }

    /**
     * Registriert die per Pairing gefundene deCONZ-Geräte-Id auf dem Gerät (Phase 4 AP4,
     * siehe kb/05-migration-plan.md "Client-Cutover"). Fachlicher Nachfolger von
     * {@code Common.Device#modify(...)} (Alt-Code: alle Gerätefelder unverändert außer der
     * neuen deCONZ-Id) - jetzt über den additiven Endpunkt
     * {@code POST /api/v1/devices/{id}/deconz-uuid} (die AP3-Inventur hatte diesen Pfad
     * übersehen, siehe kb/05-migration-plan.md, Änderungslog "Phase 4 AP4").
     */
    @Override
    public CompletableFuture<Boolean> registerDevice(ClientDevice device) {
        return scanForNewDevice().thenApply(uuid -> {
            if (uuid != null) {
                logger.info("Found new device %s. Updating database.".formatted(uuid));
                try {
                    ElwaManager.instance.getApiClient().updateDeconzUuid(device.getId(), uuid);
                } catch (ApiException e) {
                    logger.error("Failed to update device.", e);
                    return false;
                }
                return true;
            } else {
                logger.info("No new devices found.");
                return false;
            }
        });
    }

    private CompletableFuture<String> scanForNewDevice() {
        if (currentRegistrationFuture != null && !currentRegistrationFuture.isDone()) {
            throw new RuntimeException("Another registration process has already been started.");
        }
        logger.info("Scanning for new device");
        currentRegistrationFuture = new CompletableFuture<>();
        return CompletableFuture.supplyAsync(() -> {
            try {
                enablePairingForSeconds(PAIRING_TIME_SECONDS);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            return currentRegistrationFuture
                    .orTimeout(PAIRING_TIME_SECONDS, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        logger.warn("Scanning for devices failed.", ex);
                        try {
                            enablePairingForSeconds(0);
                        } catch (IOException | InterruptedException e) {
                            logger.warn("Failed to reset pairing time", e);
                        }
                        return null;
                    }).join();
        });
    }

    private void enablePairingForSeconds(int pairingSeconds) throws IOException, InterruptedException {
        var payload = gson.toJson(new DeconzConfigPariing(pairingSeconds));
        apiAdapter.request("config", r -> r.PUT(HttpRequest.BodyPublishers.ofString(payload)));
        logger.info("Pairing is now active for %s seconds.".formatted(pairingSeconds));
    }
}
