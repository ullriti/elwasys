package org.kabieror.elwasys.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt des elwasys-Backends (Phase 2 des Modernisierungsplans).
 *
 * <p>Läuft parallel zum Bestand (Client-Raspi, Portal) auf derselben PostgreSQL-Datenbank
 * (Strangler-Muster, siehe kb/05-migration-plan.md). Dieses Arbeitspaket liefert das
 * Grundgerüst: Actuator-Health, Flyway-Baseline-Migration gegen das Bestandsschema. Fachliche
 * Endpunkte/Entities folgen in späteren Arbeitspaketen.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
