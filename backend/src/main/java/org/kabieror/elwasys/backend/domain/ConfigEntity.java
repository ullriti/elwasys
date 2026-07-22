package org.kabieror.elwasys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entspricht der Schlüssel/Wert-Konfigurationstabelle {@code config} (siehe
 * docs/kb/02-data-model.md). Kein Java-Code (weder Alt noch Neu) liest diese Werte fachlich -
 * siehe docs/kb/02-data-model.md ("config.db.version stillgelegt"). Diese Entity existiert nur
 * der Vollständigkeit halber (Kerntabelle laut Arbeitsauftrag) und wird in AP2 von keinem
 * Service verwendet.
 */
@Entity
@Table(name = "config")
public class ConfigEntity {

    @Id
    @Column(name = "key", length = 50)
    private String key;

    @Column(name = "value")
    private String value;

    protected ConfigEntity() {
        // for JPA
    }

    public ConfigEntity(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
