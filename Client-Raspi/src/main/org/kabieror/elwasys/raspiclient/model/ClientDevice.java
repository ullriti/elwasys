package org.kabieror.elwasys.raspiclient.model;

import org.kabieror.elwasys.raspiclient.api.dto.DeviceOverviewDto;
import org.kabieror.elwasys.raspiclient.api.dto.ProgramDto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-seitiges Gegenstück zu {@code Common.Device} (Phase 4 AP4). Siehe
 * {@link ClientProgram} für die Begründung eigener, DB-freier Modellklassen.
 * <p>
 * <b>Objekt-Identität ist wichtig</b> (anders als bei {@link ClientProgram}/
 * {@link ClientUser}, die nur kurzlebig innerhalb eines einzelnen Bildschirms verwendet
 * werden): {@link #getCurrentExecution()} wird von {@link #onExecutionStarted}/
 * {@link #onExecutionEnded} rein lokal gepflegt (kein Feld aus der API) und muss über
 * mehrfache {@code GET /api/v1/devices/overview}-Aufrufe hinweg auf DEMSELBEN Objekt
 * bestehen bleiben, damit z. B. der 20-Sekunden-Hintergrundabgleich in
 * {@code ExecutionManager} eine laufende Ausführung wiedererkennt. Das entspricht 1:1 dem
 * Identitäts-Cache, den der Alt-{@code DataManager} intern führt (siehe
 * {@code DataManager#getDevice(int)}: liefert bei wiederholtem Laden dasselbe Objekt zurück
 * und aktualisiert nur dessen Felder) - hier nachgebildet durch
 * {@code ElwaManager#getManagedDevices()}, das denselben {@link ClientDevice} je Id aus
 * einem Cache wiederverwendet und nur {@link #updateFrom(DeviceOverviewDto)} aufruft, statt
 * bei jedem Aufruf ein neues Objekt anzulegen.
 * <p>
 * {@code equals()}/{@code hashCode()} bleiben absichtlich Objektidentität (wie beim
 * Alt-{@code Device}, das ebenfalls keine Überschreibung hat) - Verhalten bewahren.
 */
public class ClientDevice {

    private final int id;
    private String name;
    private int position;
    private boolean enabled;
    private String fhemName;
    private String fhemSwitchName;
    private String fhemPowerName;
    private String deconzUuid;
    private float autoEndPowerThreshold;
    private Duration autoEndWaitTime;
    private List<ClientProgram> programs = Collections.emptyList();
    private Integer lastUserId;
    private String lastUserName;

    /**
     * Die aktuell auf diesem Gerät laufende Ausführung - rein lokaler Zustand, siehe
     * Klassenkommentar. {@code null}, solange dieser Client keine laufende Ausführung auf
     * diesem Gerät kennt (entweder wirklich frei, oder eine anderswo laufende Ausführung
     * wurde noch nicht per {@link #onExecutionStarted} bekanntgemacht - z. B. während des
     * Wiederaufnahme-Scans beim Start, siehe {@code ElwaManager#initiate}).
     */
    private ClientExecution currentExecution;

    public ClientDevice(int id) {
        this.id = id;
    }

    public void updateFrom(DeviceOverviewDto dto) {
        this.name = dto.name();
        this.position = dto.position();
        this.enabled = dto.enabled();
        this.fhemName = dto.fhemName();
        this.fhemSwitchName = dto.fhemSwitchName();
        this.fhemPowerName = dto.fhemPowerName();
        this.deconzUuid = dto.deconzUuid();
        this.autoEndPowerThreshold = dto.autoEndPowerThreshold();
        this.autoEndWaitTime = Duration.ofSeconds(dto.autoEndWaitTimeSeconds());
        this.lastUserId = dto.lastUserId();
        this.lastUserName = dto.lastUserName();
        List<ClientProgram> newPrograms = new ArrayList<>();
        if (dto.programs() != null) {
            for (ProgramDto p : dto.programs()) {
                newPrograms.add(ClientProgram.of(p));
            }
        }
        this.programs = newPrograms;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getPosition() {
        return this.position;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getFhemName() {
        return this.fhemName;
    }

    public String getFhemSwitchName() {
        return this.fhemSwitchName;
    }

    public String getFhemPowerName() {
        return this.fhemPowerName;
    }

    public String getDeconzUuid() {
        return this.deconzUuid;
    }

    public float getAutoEndPowerThreshold() {
        return this.autoEndPowerThreshold;
    }

    public Duration getAutoEndWaitTime() {
        return this.autoEndWaitTime;
    }

    /**
     * Die ungefilterte Programmliste dieses Geräts mit ungerabattetem Preis (siehe
     * {@code DeviceOverviewDto} Javadoc) - entspricht {@code Common.Device#getPrograms()}
     * (ohne {@code User}-Parameter), verwendet von {@code ui/small} für die
     * Programmauswahl VOR dem Kartenlogin.
     */
    public List<ClientProgram> getPrograms() {
        return this.programs;
    }

    public Integer getLastUserId() {
        return this.lastUserId;
    }

    public String getLastUserName() {
        return this.lastUserName;
    }

    public ClientExecution getCurrentExecution() {
        return this.currentExecution;
    }

    public void onExecutionStarted(ClientExecution e) {
        this.currentExecution = e;
    }

    public void onExecutionEnded() {
        this.currentExecution = null;
    }
}
