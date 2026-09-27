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
     *
     * <p><b>Hintergrund</b> (Vorfall am Terminal Hilarenhaus, 2026-09-24): Ein
     * {@code Labeled} mit eingeschaltetem <b>Mnemonic-Parsing</b> merkt sich, dass sein Text
     * ein Tastenkürzel enthielt (einen Unterstrich). Bekommt es danach einen <b>leeren</b>
     * Text, rechnet JavaFX beim Layout die Breite des Kürzel-Zeichens trotzdem noch aus und
     * greift mit dem inzwischen ungültigen Index {@code -1} in die leere Zeichenkette:
     * {@code StringIndexOutOfBoundsException} mitten im Layout-Durchlauf. Nachgemessen an
     * JavaFX 17.0.20, siehe den Regressionstest {@code ConfirmationPaneLayoutTest}:
     *
     * <pre>
     * Mnemonic an,  Text mit '_' danach ""    -&gt; Absturz
     * Mnemonic an,  Text mit '_' danach " "   -&gt; kein Absturz
     * Mnemonic an,  Text ohne '_' danach ""   -&gt; kein Absturz
     * Mnemonic aus, Text mit '_' danach ""    -&gt; kein Absturz
     * </pre>
     *
     * <p>Der Schaden ist die Folge: {@code Parent.layout()} setzt vor {@code layoutChildren()}
     * das Flag {@code performingLayout} und nimmt es erst danach zurück; fliegt dazwischen eine
     * Ausnahme, bleibt es stehen und jedes spätere {@code requestLayout()} dieses Knotens wird
     * stillschweigend verworfen. Wie weit das reicht, hängt davon ab, welche Vorfahren im
     * selben Durchlauf selbst ein Layout brauchten - vom einzelnen Bedienelement bis zu einem
     * ganzen Zweig. Weil erst das Layout den Text in den gezeichneten Knoten schreibt, bleibt
     * der betroffene Bereich danach auf dem Bildschirm stehen, während seine Properties
     * unbemerkt weiterlaufen.
     *
     * <p>Diese Methode ist die <b>zweite</b> Sicherung: sie nimmt dem Absturz die Bedingung
     * "danach leer". Die erste ist {@code mnemonicParsing="false"} an den betroffenen
     * Bedienelementen (siehe {@code ConfirmationPane.fxml}) - ein Touch-Terminal ohne Tastatur
     * braucht keine Tastenkürzel. {@code Label} hat Mnemonic-Parsing ohnehin aus; deshalb ist
     * ein leerer Text an einer reinen Beschriftung ungefährlich und im Bestand auch üblich.
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
