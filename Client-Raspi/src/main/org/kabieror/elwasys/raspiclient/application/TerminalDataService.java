package org.kabieror.elwasys.raspiclient.application;

import org.kabieror.elwasys.raspiclient.api.ApiClient;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceDto;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceOverviewDto;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionDto;
import org.kabieror.elwasys.raspiclient.api.dto.UserDto;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.model.ClientUser;
import org.kabieror.elwasys.raspiclient.offline.OfflineGateway;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Der fachliche Datenzugriff des Terminals: jede Abfrage geht zuerst über die REST-API v1
 * ({@link ApiClient}) und fällt NUR bei einem Kommunikationsfehler
 * ({@link ApiException#isCommunicationFailure()}) auf den lokalen Offline-Snapshot bzw. das
 * Ereignis-Journal zurück (Phase 4 AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze:
 * Offline-Buchungen am Terminal").
 * <p>
 * Herausgelöst aus {@link ElwaManager} (#91): dort lag diese Geschäftslogik zwischen
 * Konfigurations-, Lebenszyklus- und Listener-Verwaltung und war ohne den Singleton nicht
 * erreichbar. {@link ElwaManager} delegiert die vier Methoden jetzt hierher, der Vertrag nach
 * außen (dieselben DTO-/Exception-Typen) ist unverändert.
 */
public class TerminalDataService {

    private final ApiClient apiClient;
    private final OfflineGateway offlineGateway;

    /**
     * Identitäts-Cache der von diesem Client verwalteten Geräte, je Geräte-Id (Phase 4 AP4).
     * Bildet den Identitäts-Cache nach, den {@code Common.DataManager} intern führte (siehe
     * {@link ClientDevice} Klassenkommentar) - wichtig, damit
     * {@link ClientDevice#getCurrentExecution()} über mehrere {@link #getManagedDevices()}-Aufrufe
     * hinweg konsistent bleibt.
     */
    private final Map<Integer, ClientDevice> deviceCache = new ConcurrentHashMap<>();

    /**
     * Merkt sich den zuletzt geladenen Übersichts-Datensatz je Gerät, damit
     * {@link ElwaManager#initiate()} nach {@link #getManagedDevices()} nicht dieselben Daten ein
     * zweites Mal laden muss.
     */
    private final Map<Integer, DeviceOverviewDto> lastOverview = new ConcurrentHashMap<>();

    public TerminalDataService(ApiClient apiClient, OfflineGateway offlineGateway) {
        this.apiClient = apiClient;
        this.offlineGateway = offlineGateway;
    }

    /**
     * Kartenlogin: online über die API, oder - falls das Backend nicht erreichbar ist - offline
     * gegen den zuletzt geladenen Snapshot. Liefert denselben {@link UserDto}-Vertrag wie
     * {@code ApiClient#cardLogin} direkt.
     */
    public UserDto cardLogin(String cardId) throws ApiException {
        try {
            return this.apiClient.cardLogin(cardId);
        } catch (ApiException e) {
            if (!e.isCommunicationFailure()) {
                throw e;
            }
            return this.offlineGateway.cardLogin(cardId, e);
        }
    }

    /**
     * Die für einen Benutzer nutzbaren Geräte: online über die API, oder offline gegen den
     * Snapshot. Liefert denselben {@link DeviceDto}-Vertrag wie {@code ApiClient#getDevices}
     * direkt.
     */
    public List<DeviceDto> getDevicesForUser(int userId) throws ApiException {
        try {
            return this.apiClient.getDevices(userId);
        } catch (ApiException e) {
            if (!e.isCommunicationFailure()) {
                throw e;
            }
            return this.offlineGateway.getDevicesForUser(userId, e);
        }
    }

    /**
     * Bucht eine neue Ausführung: online über die API, oder - falls das Backend nicht erreichbar
     * ist - offline gegen den Snapshot (Stufe B der Offline-Konzeptskizze). Der
     * Idempotenz-Schlüssel wird VOR dem eigentlichen Versuch erzeugt (statt wie sonst erst
     * innerhalb von {@code ApiClient}), damit er im Fehlerfall auch für den
     * Offline-Journal-Eintrag zur Verfügung steht.
     */
    public ClientExecution createExecution(ClientUser user, ClientDevice device, ClientProgram program)
            throws ApiException {
        LocalDateTime clientTimestamp = LocalDateTime.now();
        String idempotencyKey = UUID.randomUUID().toString();
        try {
            ExecutionDto dto = this.apiClient.createExecution(user.getId(), device.getId(), program.getId(),
                    clientTimestamp, idempotencyKey);
            return ClientExecution.of(dto, device, program, user);
        } catch (ApiException e) {
            if (!e.isCommunicationFailure()) {
                throw e;
            }
            return this.offlineGateway.createExecution(user, device, program, clientTimestamp, idempotencyKey, e);
        }
    }

    /**
     * Gibt alle Geräte zurück, die von diesem Client verwaltet werden sollen. Behält dabei die
     * Objekt-Identität je Geräte-Id über mehrere Aufrufe hinweg bei (siehe {@link ClientDevice}
     * Klassenkommentar). Ist das Backend nicht erreichbar, wird auf die im letzten Snapshot
     * enthaltenen Geräte zurückgefallen, sofern dieser noch nutzbar ist.
     */
    public List<ClientDevice> getManagedDevices() throws ApiException {
        List<DeviceOverviewDto> overview;
        try {
            overview = this.apiClient.getDevicesOverview();
        } catch (ApiException e) {
            if (!e.isCommunicationFailure()) {
                throw e;
            }
            overview = this.offlineGateway.getDevicesOverview(e);
        }
        List<ClientDevice> result = new ArrayList<>(overview.size());
        for (DeviceOverviewDto dto : overview) {
            ClientDevice device = this.deviceCache.computeIfAbsent(dto.id(), ClientDevice::new);
            device.updateFrom(dto);
            this.lastOverview.put(dto.id(), dto);
            result.add(device);
        }
        return result;
    }

    /**
     * Der zuletzt über {@link #getManagedDevices()} geladene Übersichts-Datensatz eines Geräts,
     * oder {@code null}, wenn dieses Gerät noch nie geladen wurde.
     */
    public DeviceOverviewDto lastOverviewFor(int deviceId) {
        return this.lastOverview.get(deviceId);
    }
}
