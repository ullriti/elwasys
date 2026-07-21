package org.kabieror.elwasys.raspiclient.api.dto;

/**
 * Gegenstück zu {@code backend.api.dto.SnapshotUserGroupDto} (Phase 4 AP6, siehe
 * kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"). Enthält die
 * Rabattregel, damit {@code offline.OfflinePricing} dieselbe Preisberechnung wie das
 * Backend ({@code PricingService}) offline nachvollziehen kann.
 */
public record SnapshotUserGroupDto(Integer id, String name, DiscountType discountType, double discountValue) {
}
