package org.kabieror.elwasys.backend.api.dto;

import org.kabieror.elwasys.backend.domain.DeviceEntity;

/**
 * Anonyme (benutzerunabhängige) Geräteübersicht des Standorts eines Terminal-Tokens (AP3,
 * Phase 4, siehe kb/05-migration-plan.md "Arbeitspakete Phase 4", AP3 sowie
 * kb/03-modules.md, Inventur-Tabelle). Anders als {@link DeviceDto} (das {@code userId}
 * voraussetzt, siehe {@code DeviceController#list}) deckt dieses DTO die Ladepfade ab, die
 * im Alt-Client VOR einem bekannten Benutzer ablaufen:
 *
 * <ul>
 *   <li>Geräteauswahl-Bildschirm (Zustand {@code SELECT_DEVICE}, sowohl {@code ui/medium}
 *       als auch {@code ui/small}) - der Alt-Client zeigt Gerätekacheln bereits an, bevor
 *       eine Karte gescannt wurde ({@code DataManager#getDevicesToDisplay}/
 *       {@code #getDevicesToDisplayXs}, kein Benutzerbezug).</li>
 *   <li>Der 20-Sekunden-Hintergrundabgleich in {@code ExecutionManager}, der bei JEDEM
 *       Gerät ohne laufende Ausführung prüft, ob dessen Steckdose fälschlich eingeschaltet
 *       ist - läuft unabhängig von jeder Benutzer-Session.</li>
 *   <li>Der "letzter Benutzer"-Hinweis je Gerätekachel ({@code DeviceListEntry#onStart},
 *       entspricht {@code DataManager#getLastUser(Device)} - siehe {@link #lastUserId()}/
 *       {@link #lastUserName()}, hier direkt eingebettet statt eines eigenen Endpunkts).</li>
 *   <li>Der Wiederaufnahme-Scan beim Terminal-Start (Testfall C13: eine beim letzten
 *       Herunterfahren unterbrochene Ausführung wird fortgesetzt) - entspricht
 *       {@code ElwaManager#initiate}, das für jedes Gerät
 *       {@code DataManager#getRunningExecution(Device)} abfragt. {@link #runningExecutionId()}
 *       liefert die Id, mit der der Client anschließend {@code GET /api/v1/executions/{id}}
 *       für die vollen Details aufruft (kein eigener Resume-Endpunkt nötig).</li>
 * </ul>
 *
 * <p>Enthält bewusst {@code KEINE} {@code programs}/{@code usableByUser}-Felder (die
 * setzen einen bekannten Benutzer voraus) - nach einem Kartenlogin ruft der Client
 * stattdessen wie bisher {@code GET /api/v1/devices?userId=...} auf, um die
 * benutzerbezogene Sicht (verfügbare Programme, Nutzbarkeit) nachzuladen.
 */
public record DeviceOverviewDto(Integer id, String name, int position, boolean enabled, boolean occupied,
        Integer runningExecutionId, Integer lastUserId, String lastUserName, String fhemName, String fhemSwitchName,
        String fhemPowerName, String deconzUuid, float autoEndPowerThreashold, int autoEndWaitTimeSeconds) {

    public static DeviceOverviewDto of(DeviceEntity device, boolean occupied, Integer runningExecutionId,
            Integer lastUserId, String lastUserName) {
        return new DeviceOverviewDto(device.getId(), device.getName(), device.getPosition(), device.isEnabled(),
                occupied, runningExecutionId, lastUserId, lastUserName, device.getFhemName(),
                device.getFhemSwitchName(), device.getFhemPowerName(), device.getDeconzUuid(),
                device.getAutoEndPowerThreashold(), device.getAutoEndWaitTimeSeconds());
    }
}
