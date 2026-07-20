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
 * kb/02-data-model.md) sowie {@code org.kabieror.elwasys.common.Location} im Alt-Code.
 *
 * <p>Die Spalten {@code client_uid}/{@code client_ip}/{@code client_port}/
 * {@code client_last_seen} (Registry für die Fernwartungs-Anwahl, siehe
 * kb/05-migration-plan.md Komponenten-Inventur: "Weg - obsolet durch ausgehende
 * Client-Verbindung") werden hier bewusst NICHT gemappt: sie sind fürs AP2-Fachkern
 * (Abrechnung/Berechtigungen/Preisberechnung/Execution-Lebenszyklus) irrelevant, alle
 * nullable, und werden in Phase 5 entfernt.
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
