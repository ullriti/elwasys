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
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/**
 * Entspricht der Tabelle {@code locations} (ein Standort = ein Client-Terminal, siehe
 * docs/kb/02-data-model.md) sowie {@code org.kabieror.elwasys.common.Location} im Alt-Code.
 *
 * <p>Die Spalten {@code client_uid}/{@code client_ip}/{@code client_port}/
 * {@code client_last_seen} (Registry für die alte Fernwartungs-Anwahl, siehe
 * docs/kb/05-migration-plan.md Komponenten-Inventur: "Weg - obsolet durch ausgehende
 * Client-Verbindung") wurden hier nie gemappt und sind seit Phase 5 AP3
 * ({@code V9__drop_obsolete_location_client_columns.sql}) auch in der Datenbank entfernt.
 */
@Entity
@Table(name = "locations")
public class LocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    /**
     * n:m-Tabelle {@code locations_valid_user_groups}: welche Benutzergruppen dürfen an
     * diesem Standort einen Client benutzen (siehe {@code MainFormController} im Client:
     * Login wird verweigert, wenn die Gruppe des Benutzers nicht in dieser Liste steht).
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "locations_valid_user_groups",
            joinColumns = @JoinColumn(name = "location_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<UserGroupEntity> validUserGroups = new HashSet<>();

    /**
     * Maximale Dauer (in Minuten), für die ein Terminal dieses Standorts ohne
     * Backend-Verbindung eigenständig NEUE Buchungen akzeptiert ("offline.max-duration",
     * Phase 4 AP6, additive Migration {@code V5__add_offline_max_duration_to_locations.sql}
     * - siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal" und
     * "Festlegungen zu den Offline-Detailfragen"). Danach lehnt das Terminal neue Buchungen
     * ab (Fehlerbild wie C15); bereits laufende Ausführungen werden unabhängig davon
     * weiterhin lokal zu Ende geführt und nachgemeldet. Default 60 (Auftraggeber-Vorgabe),
     * im Portal je Standort editierbar (siehe {@code LocationFormDialog}), an das Terminal
     * über {@code SnapshotDto#offlineMaxDurationMinutes()} ausgeliefert.
     */
    @Column(name = "offline_max_duration_minutes", nullable = false)
    private Integer offlineMaxDurationMinutes = 60;

    protected LocationEntity() {
        // for JPA
    }

    public LocationEntity(String name) {
        this.name = name;
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

    public Set<UserGroupEntity> getValidUserGroups() {
        return this.validUserGroups;
    }

    public Integer getOfflineMaxDurationMinutes() {
        return this.offlineMaxDurationMinutes;
    }

    public void setOfflineMaxDurationMinutes(Integer offlineMaxDurationMinutes) {
        this.offlineMaxDurationMinutes = offlineMaxDurationMinutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocationEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
