package org.kabieror.elwasys.raspiclient.api.dto;

/**
 * Wire-kompatibles Gegenstück zu {@code backend.domain.DiscountType} (Phase 4 AP6, siehe
 * docs/kb/05-migration-plan.md). Bewusst ein eigenes, kleines Enum statt einer Wiederverwendung
 * von {@code Common.DiscountType}: dessen Konstanten heißen {@code None}/{@code Fix}/
 * {@code Factor} (gemischte Groß-/Kleinschreibung), während das Backend-DTO
 * {@code NONE}/{@code FIX}/{@code FACTOR} (Großbuchstaben, 1:1 zum Postgres-Enum) sendet -
 * Gson matcht Enum-Konstanten exakt nach Namen, eine Wiederverwendung würde die
 * Deserialisierung von {@link SnapshotUserGroupDto#discountType()} stillschweigend
 * fehlschlagen lassen.
 */
public enum DiscountType {
    NONE, FIX, FACTOR
}
