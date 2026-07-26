package org.kabieror.elwasys.backend.ui.component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Gemeinsame Anzeigeformate des Portals (Beträge, Zeitpunkte).
 *
 * <p>Warum zentral: Geldbeträge und Zeitpunkte tauchen in nahezu jeder View und jedem Dialog
 * auf und wurden bis Issue #89 in jeder Klasse einzeln formatiert (Review-Befund "strukturelle
 * Duplikation in den Admin-Views"). Jede Kopie war eine Gelegenheit, im selben Portal zwei
 * verschiedene Schreibweisen für dieselbe Zahl zu zeigen. Die Formate sind unverändert die
 * bisher überall verwendeten: deutsche Währungsschreibweise ({@code 1,50 €}) bzw. kurzes
 * deutsches Datum/Zeit-Format.
 */
public final class PortalFormats {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.SHORT).withLocale(Locale.GERMANY);

    private PortalFormats() {
    }

    /**
     * Betrag in deutscher Währungsschreibweise. {@code null} wird zu "-" (nicht jeder Betrag im
     * Portal ist gesetzt, z.B. ein Offline-Vorfall ohne bekannten Preis).
     */
    public static String currency(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        // NumberFormat ist nicht threadsicher - deshalb bewusst je Aufruf eine neue Instanz
        // (genau wie zuvor in jeder einzelnen View).
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount);
    }

    /** Zeitpunkt im kurzen deutschen Format; {@code null} wird zu "-". */
    public static String dateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DATE_TIME_FORMAT);
    }
}
