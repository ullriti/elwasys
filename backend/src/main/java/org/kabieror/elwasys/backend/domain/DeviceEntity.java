package org.kabieror.elwasys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/**
 * Entspricht der Tabelle {@code devices} (siehe kb/02-data-model.md) sowie
 * {@code org.kabieror.elwasys.common.Device} im Alt-Code.
 *
 * <p>Die Spalte {@code auto_end_power_threashold} behält den Tippfehler des
 * Bestandsschemas bei (siehe Rahmenbedingungen: keine Schema-Änderungen in AP2; die
 * Umbenennung ist für Phase 5 vorgesehen).
 */
@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int position;

    /**
     * EAGER: siehe {@link org.kabieror.elwasys.backend.domain.UserEntity#getGroup()} -
     * der Alt-Code lädt den Standort eines Geräts immer sofort mit.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private LocationEntity location;

    @Column(name = "fhem_name", nullable = false, length = 50)
    private String fhemName = "";

    @Column(name = "fhem_switch_name", nullable = false, length = 50)
    private String fhemSwitchName = "";

    @Column(name = "fhem_power_name", nullable = false, length = 50)
    private String fhemPowerName = "";

    @Column(name = "deconz_uuid", length = 64)
    private String deconzUuid = "";

    /**
     * Tippfehler ("threashold" statt "threshold") ist Teil des Bestandsschemas und bleibt
     * bewusst erhalten, siehe Klassenkommentar.
     */
    @Column(name = "auto_end_power_threashold", nullable = false)
    private float autoEndPowerThreashold = 0.5f;

    /**
     * Wartezeit nach Unterschreiten des Leistungs-Grenzwerts in Sekunden (Spalte ist ein
     * INTEGER; siehe {@code Program}/{@code Execution} im Alt-Code für die Umrechnung in
     * {@link java.time.Duration}, die in den Services dieses Moduls nachgebildet wird).
     */
    @Column(name = "auto_end_wait_time", nullable = false)
    private int autoEndWaitTimeSeconds = 20;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * n:m-Tabelle {@code devices_valid_user_groups}. EAGER wie die übrigen n:m-Relationen
     * dieses Moduls - siehe {@link #location}.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "devices_valid_user_groups",
            joinColumns = @JoinColumn(name = "device_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<UserGroupEntity> validUserGroups = new HashSet<>();

    /**
     * n:m-Tabelle {@code device_program_rel}: die auf diesem Gerät verfügbaren Programme.
     * EAGER, siehe {@link #location}.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "device_program_rel",
            joinColumns = @JoinColumn(name = "device_id"),
            inverseJoinColumns = @JoinColumn(name = "program_id"))
    private Set<ProgramEntity> programs = new HashSet<>();

    protected DeviceEntity() {
        // for JPA
    }

    public DeviceEntity(String name, int position, LocationEntity location) {
        this.name = name;
        this.position = position;
        this.location = location;
    }

    public Integer getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPosition() {
        return this.position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public LocationEntity getLocation() {
        return this.location;
    }

    public void setLocation(LocationEntity location) {
        this.location = location;
    }

    public String getFhemName() {
        return this.fhemName;
    }

    public void setFhemName(String fhemName) {
        this.fhemName = fhemName;
    }

    public String getFhemSwitchName() {
        return this.fhemSwitchName;
    }

    public void setFhemSwitchName(String fhemSwitchName) {
        this.fhemSwitchName = fhemSwitchName;
    }

    public String getFhemPowerName() {
        return this.fhemPowerName;
    }

    public void setFhemPowerName(String fhemPowerName) {
        this.fhemPowerName = fhemPowerName;
    }

    public String getDeconzUuid() {
        return this.deconzUuid;
    }

    public void setDeconzUuid(String deconzUuid) {
        this.deconzUuid = deconzUuid;
    }

    public float getAutoEndPowerThreashold() {
        return this.autoEndPowerThreashold;
    }

    public void setAutoEndPowerThreashold(float autoEndPowerThreashold) {
        this.autoEndPowerThreashold = autoEndPowerThreashold;
    }

    public int getAutoEndWaitTimeSeconds() {
        return this.autoEndWaitTimeSeconds;
    }

    public void setAutoEndWaitTimeSeconds(int autoEndWaitTimeSeconds) {
        this.autoEndWaitTimeSeconds = autoEndWaitTimeSeconds;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<UserGroupEntity> getValidUserGroups() {
        return this.validUserGroups;
    }

    public Set<ProgramEntity> getPrograms() {
        return this.programs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeviceEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
