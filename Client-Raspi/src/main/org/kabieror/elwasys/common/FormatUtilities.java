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
    private static NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY);

    public static String formatCurrency(BigDecimal value) {
        return currencyFormat.format(value);
    }

    public static String formatCurrency(double value) {
        return currencyFormat.format(value);
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
