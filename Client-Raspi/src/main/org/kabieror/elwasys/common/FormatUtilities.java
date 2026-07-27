package org.kabieror.elwasys.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Locale;

/**
 * Diese Klasse stellt Methoden zum Formatieren von Werten bereit.
 *
 * @author Oliver Kabierschke
 */
public class FormatUtilities {
    /**
     * Die Anzeige-Locale des Terminals - die EINE Stelle, an der diese Regel steht.
     * <p>
     * Bewusst fest und nicht die Standard-Locale der JVM: das Terminal steht in einer deutschen
     * Waschkueche, nicht dort, wo das Pi-Image gerade gebootet wurde. Der Startbefehl setzt die
     * JVM-Locale passend dazu (siehe {@code deploy/terminal/run-sh.lib.sh}), aber darauf allein
     * darf sich die Anzeige nicht verlassen: ein Bestandsgeraet kann noch mit einem aelteren
     * Startbefehl ohne die Flags laufen.
     * <p>
     * Zeitangaben zogen frueher die Standard-Locale, Geldbetraege daneben fest Deutsch - auf
     * einem englischen Image standen so US-Datum und deutscher Betrag im selben Dialog
     * (Issue #100). Neue Anzeige-Formatter binden sich an diese Konstante.
     */
    public static final Locale DISPLAY_LOCALE = Locale.GERMANY;

    /**
     * Geldbetraege durchgaengig in deutscher Schreibweise ({@code 12,34 €}).
     * <p>
     * Bewusst je Aufruf eine neue Instanz statt eines geteilten statischen Formatters:
     * {@link NumberFormat} ist nicht threadsicher, und die Aufrufer sitzen zwar heute alle auf
     * dem FX-Thread, das ist aber nichts, worauf sich die naechste Aenderung verlassen sollte.
     */
    public static String formatCurrency(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(DISPLAY_LOCALE).format(value);
    }

    public static String formatCurrency(double value) {
        return NumberFormat.getCurrencyInstance(DISPLAY_LOCALE).format(value);
    }

    public static String formatDuration(Duration d, boolean appendTimeUnit) {
        long secs = d.getSeconds();
        if (secs >= 3600) {
            return String.format("%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60) +
                    (appendTimeUnit ? " h" : "");
        } else {
            return String.format("%02d:%02d", secs / 60, secs % 60) + (appendTimeUnit ? " min" : "");
        }
    }
}
