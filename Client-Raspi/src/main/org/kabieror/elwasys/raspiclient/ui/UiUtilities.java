package org.kabieror.elwasys.raspiclient.ui;

import javafx.beans.property.StringProperty;
import javafx.scene.Node;

/**
 * Hilfsmethoden für die Arbeit mit der Benutzeroberfläche
 *
 * @author Oliver Kabierschke
 */
public class UiUtilities {
    /**
     * Platzhalter für eine Beschriftung, die nichts anzeigen soll: ein geschütztes
     * Leerzeichen. Sieht auf dem Bildschirm wie ein leerer Text aus, ist aber keiner
     * - siehe {@link #setLabelText(StringProperty, String)}.
     */
    public static final String BLANK_LABEL_TEXT = "\u00a0";

    /**
     * Setzt den Text einer Beschriftung und ersetzt dabei {@code null} und den Leerstring
     * durch {@link #BLANK_LABEL_TEXT}.
     * <p>
     * Hintergrund (Vorfall am Terminal Hilarenhaus, 2026-09-24): JavaFX rechnet beim Layout
     * einer {@code Labeled}-Beschriftung die Kürzung mit Auslassungszeichen aus. Ist der Text
     * dabei leer, entsteht in {@code LabeledSkinBase.layoutLabelInArea} ein
     * {@code substring(0, -1)} und damit eine {@link StringIndexOutOfBoundsException} -
     * mitten im Layout-Durchlauf des JavaFX-Threads. Dessen Folge ist der eigentliche Schaden:
     * {@code Parent.layout()} setzt vor {@code layoutChildren()} das Flag
     * {@code performingLayout} und nimmt es erst danach zurück; fliegt dazwischen eine
     * Ausnahme, bleibt das Flag stehen und jedes spätere {@code requestLayout()} dieses
     * Teilbaums wird stillschweigend verworfen. Der Bereich friert ein: die Text-Properties
     * werden weiter aktualisiert, auf dem Bildschirm ändert sich aber nichts mehr. Genau so
     * zeigte die Bestätigungsseite drei Tage lang Namen und Guthaben eines längst
     * abgemeldeten Benutzers, während Anmeldung und Buchung korrekt liefen.
     * <p>
     * Deshalb: an einer Beschriftung, die im Layout bleibt, nie einen leeren Text setzen.
     *
     * @param property Die Property, an der die Beschriftung hängt.
     * @param text     Der anzuzeigende Text, auch {@code null} oder leer.
     */
    public static void setLabelText(StringProperty property, String text) {
        property.set(text == null || text.isEmpty() ? BLANK_LABEL_TEXT : text);
    }

    /**
     * Setzt oder entfernt eine Style-Klasse
     */
    public static void setStyleClass(Node n, String styleClass, boolean set) {
        boolean contains = n.getStyleClass().contains(styleClass);
        if (set && !contains) {
            n.getStyleClass().add(styleClass);
        } else if (!set && contains) {
            n.getStyleClass().remove(styleClass);
        }
    }
}
