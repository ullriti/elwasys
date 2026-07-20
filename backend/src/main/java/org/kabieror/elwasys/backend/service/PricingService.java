package org.kabieror.elwasys.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.UnsupportedTemporalTypeException;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.springframework.stereotype.Service;

/**
 * 1:1-Portierung von {@code org.kabieror.elwasys.common.Program#getPrice}/
 * {@code #getDynamicPrice} (siehe kb/05-migration-plan.md, AP2). Enthält absichtlich
 * dieselben Rundungs-/Rechen-Eigenheiten wie der Alt-Code (siehe Kommentare unten) - das
 * ist Verhalten-bewahren, keine Nachlässigkeit.
 */
@Service
public class PricingService {

    /**
     * Gibt den Preis eines Programms für eine gegebene Dauer und einen Benutzer zurück.
     * 1:1-Portierung von {@code Program#getPrice(Duration, User)}.
     *
     * @param program  das Programm (entspricht {@code this} im Alt-Code)
     * @param duration die abzurechnende Dauer
     * @param user     der Benutzer, dessen Gruppenrabatt angewendet werden soll, oder
     *                 {@code null} für "kein Rabatt" (Alt-Code: {@code User.getAnonymous()},
     *                 dessen Gruppe {@code DiscountType.None} hat und daher ebenfalls keinen
     *                 Rabatt anwendet - siehe Klassenkommentar von
     *                 {@code User.getAnonymous()} im Alt-Code)
     * @return der Preis, oder {@code null}, wenn der Programmtyp unbekannt ist (kann in der
     *         Praxis nicht vorkommen, da {@link ProgramEntity#getType()} niemals
     *         {@code null} ist bei einem aus der DB geladenen Programm)
     */
    public BigDecimal getPrice(ProgramEntity program, Duration duration, UserEntity user) {
        Duration freeDuration = Duration.ofSeconds(program.getFreeDurationSeconds());
        if (duration.compareTo(freeDuration) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal price;
        switch (program.getType()) {
            case DYNAMIC:
                price = getDynamicPrice(program, duration);
                break;
            case FIXED:
                price = program.getFlagfall();
                break;
            default:
                price = null;
        }
        if (price == null) {
            return null;
        }

        UserGroupEntity group = user == null ? null : user.getGroup();
        if (group == null) {
            // Kein Benutzer / keine Gruppe -> kein Rabatt (entspricht der Offline-Gruppe des
            // Alt-Codes, die stets DiscountType.None trägt).
            return price;
        }
        if (group.getDiscountType() == DiscountType.FACTOR) {
            // new BigDecimal(double) statt BigDecimal.valueOf(double): bewusst identisch zum
            // Alt-Code, der ebenfalls den (binär ungenauen) double-Wert direkt in ein
            // BigDecimal übernimmt statt über dessen String-Repräsentation zu gehen. Das kann
            // zu vielen Nachkommastellen führen (z.B. 0.1 -> 0.1000000000000000055511...) -
            // Verhalten bewahren erfordert exakt dieses Ergebnis.
            return price.subtract(price.multiply(new BigDecimal(group.getDiscountValue())));
        } else if (group.getDiscountType() == DiscountType.FIX) {
            return price.subtract(new BigDecimal(group.getDiscountValue()));
        } else {
            return price;
        }
    }

    /**
     * 1:1-Portierung von {@code Program#getDynamicPrice}. Die Ganzzahldivision bei
     * MINUTES/HOURS (z.B. {@code duration.getSeconds() / 60}) ist im Alt-Code bewusst so
     * (kein Runden, sondern Abschneiden Richtung Null) - dieses Verhalten wird hier exakt
     * übernommen, auch wenn es z.B. bei 90 Sekunden mit Zeiteinheit MINUTES einen Faktor
     * von 1 statt 1.5 ergibt.
     */
    private BigDecimal getDynamicPrice(ProgramEntity program, Duration duration) {
        final BigDecimal factor;
        switch (program.getTimeUnit()) {
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
                        "The temporal unit " + program.getTimeUnit() + " is not supported.");
        }
        return program.getFlagfall().add(program.getRate().multiply(factor));
    }
}
