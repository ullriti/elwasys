package org.kabieror.elwasys.backend.api.dto;

import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;

/**
 * Teil des {@link SnapshotDto} (AP3, Phase 4): eine der am Standort zugelassenen
 * Benutzergruppen inkl. Rabattregel - nötig, damit ein Terminal offline dieselbe
 * Preisberechnung wie {@link org.kabieror.elwasys.backend.service.PricingService}
 * nachvollziehen könnte (Vorbereitung für die Offline-Buchungs-Vertiefung in AP6, siehe
 * docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal").
 */
public record SnapshotUserGroupDto(Integer id, String name, DiscountType discountType, double discountValue) {

    public static SnapshotUserGroupDto of(UserGroupEntity group) {
        return new SnapshotUserGroupDto(group.getId(), group.getName(), group.getDiscountType(),
                group.getDiscountValue());
    }
}
