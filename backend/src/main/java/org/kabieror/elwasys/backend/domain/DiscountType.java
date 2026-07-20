package org.kabieror.elwasys.backend.domain;

/**
 * Rabatt-Typ einer Benutzergruppe. Spiegelt das Postgres-Enum {@code DISCOUNT_TYPE}
 * (siehe Flyway-Baseline, Tabelle {@code user_groups}) sowie den Alt-Code
 * {@code org.kabieror.elwasys.common.DiscountType} (dort andere Konstantennamen:
 * {@code None}/{@code Fix}/{@code Factor} - hier bewusst identisch zu den DB-Werten
 * benannt, damit Hibernates native Postgres-Enum-Unterstützung (siehe
 * {@link UserGroupEntity#discountType}) ohne zusätzliche Übersetzungstabelle
 * funktioniert).
 */
public enum DiscountType {
    NONE, FIX, FACTOR
}
