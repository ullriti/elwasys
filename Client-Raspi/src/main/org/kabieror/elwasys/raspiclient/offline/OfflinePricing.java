package org.kabieror.elwasys.raspiclient.offline;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.UnsupportedTemporalTypeException;
import org.kabieror.elwasys.common.ProgramType;
import org.kabieror.elwasys.raspiclient.api.dto.DiscountType;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotProgramDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotUserGroupDto;

/**
 * 1:1-Portierung von {@code backend.service.PricingService} für die Offline-Preisberechnung
 * (Phase 4 AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am
 * Terminal"). Nötig, weil das Terminal offline keine vom Server vorberechneten Preise
 * bekommt (anders als {@code DeviceDto#programs()}/{@code DeviceOverviewDto#programs()} im
 * Online-Betrieb) - {@code offline.OfflineGateway} baut die entsprechenden DTOs stattdessen
 * selbst aus dem {@link SnapshotProgramDto}/{@link SnapshotUserGroupDto} des Snapshots auf,
 * mit hier berechnetem Preis.
 */
final class OfflinePricing {

    private OfflinePricing() {
    }

    static BigDecimal priceAtMaxDuration(SnapshotProgramDto program, SnapshotUserGroupDto group) {
        return price(program, Duration.ofSeconds(program.maxDurationSeconds()), group);
    }

    static BigDecimal price(SnapshotProgramDto program, Duration duration, SnapshotUserGroupDto group) {
        Duration freeDuration = Duration.ofSeconds(program.freeDurationSeconds());
        if (duration.compareTo(freeDuration) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal price;
        if (program.type() == ProgramType.DYNAMIC) {
            price = dynamicPrice(program, duration);
        } else if (program.type() == ProgramType.FIXED) {
            price = program.flagfall();
        } else {
            // OPEN_DOOR (die "Tuer oeffnen"-Funktion) landet nie im Snapshot - siehe
            // SnapshotProgramDto, das nur echte Backend-Programme abbildet.
            return BigDecimal.ZERO;
        }

        if (group == null || group.discountType() == DiscountType.NONE) {
            return price;
        } else if (group.discountType() == DiscountType.FACTOR) {
            // Bewusst wie PricingService: new BigDecimal(double) statt BigDecimal.valueOf,
            // fuer identisches Rundungsverhalten zum Online-Pfad.
            return price.subtract(price.multiply(new BigDecimal(group.discountValue())));
        } else {
            return price.subtract(new BigDecimal(group.discountValue()));
        }
    }

    private static BigDecimal dynamicPrice(SnapshotProgramDto program, Duration duration) {
        final BigDecimal factor;
        switch (program.timeUnit()) {
            case SECONDS:
                factor = new BigDecimal(duration.getSeconds());
                break;
            case MINUTES:
                factor = new BigDecimal(duration.getSeconds() / 60);
                break;
            case HOURS:
                factor = new BigDecimal(duration.getSeconds() / 3600);
                break;
            default:
                throw new UnsupportedTemporalTypeException(
                        "Die Zeiteinheit " + program.timeUnit() + " wird nicht unterstuetzt.");
        }
        return program.flagfall().add(program.rate().multiply(factor));
    }
}
