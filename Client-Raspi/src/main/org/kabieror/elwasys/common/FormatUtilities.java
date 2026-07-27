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
     * Geldbetraege durchgaengig in deutscher Schreibweise ({@code 12,34 €}), unabhaengig von der
     * Standard-Locale der JVM - das Terminal steht in einer deutschen Waschkueche, nicht dort, wo
     * das Pi-Image gerade gebootet wurde. Der Startbefehl setzt die JVM-Locale passend dazu
     * (siehe {@code Client-Raspi/setup.sh}).
     * <p>
     * Bewusst je Aufruf eine neue Instanz statt eines geteilten statischen Formatters:
     * {@link NumberFormat} ist nicht threadsicher, und die Aufrufer sitzen zwar heute alle auf
     * dem FX-Thread, das ist aber nichts, worauf sich die naechste Aenderung verlassen sollte.
     */
    public static String formatCurrency(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value);
    }

    public static String formatCurrency(double value) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value);
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
