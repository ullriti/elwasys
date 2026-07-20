package org.kabieror.elwasys.backend.domain;

/**
 * Zeiteinheit des Zeitpreises eines DYNAMIC-Programms. Spiegelt das Postgres-Enum
 * {@code TIME_UNIT_TYPE} (Tabelle {@code programs}, Spalte {@code time_unit}).
 */
public enum TimeUnitType {
    SECONDS, MINUTES, HOURS
}
