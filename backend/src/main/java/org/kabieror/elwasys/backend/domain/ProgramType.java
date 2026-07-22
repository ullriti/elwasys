package org.kabieror.elwasys.backend.domain;

/**
 * Programmtyp. Spiegelt das Postgres-Enum {@code PROGRAM_TYPE} (Tabelle {@code programs}).
 *
 * <p>Beobachtung (siehe docs/kb/05-migration-plan.md): Der Alt-Code
 * ({@code org.kabieror.elwasys.common.ProgramType}) kennt zusätzlich den Wert
 * {@code OPEN_DOOR} - dieser wird aber nie in die Datenbank geschrieben (er existiert nur
 * für ein virtuelles, nicht-persistiertes "Tür öffnen"-Programm, das der Client rein lokal
 * erzeugt, siehe {@code Program.getDoorOpenProgram()}). Das DB-Enum kennt nur FIXED/DYNAMIC;
 * "Tür öffnen" bleibt daher außerhalb dieser Entity-Aufzählung (hardwarenahe
 * Terminal-Angelegenheit, nicht Teil der Backend-Persistenz).
 */
public enum ProgramType {
    FIXED, DYNAMIC
}
