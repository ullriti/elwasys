package org.kabieror.elwasys.raspiclient.ui.medium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.ConfirmationViewController;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Regressionstest zum Layout-Absturz vom 2026-09-24 (Terminal Hilarenhaus).
 *
 * <p>Ein {@code Labeled} mit eingeschaltetem Mnemonic-Parsing merkt sich, dass sein Text ein
 * Tastenkürzel enthielt (einen Unterstrich). Bekommt es danach einen leeren Text, greift JavaFX
 * beim Layout mit dem ungültig gewordenen Index {@code -1} in die leere Zeichenkette -
 * {@code StringIndexOutOfBoundsException} mitten im Layout-Durchlauf. Auf dieser Seite traf das
 * die E-Mail-Checkbox: ihr Text ist {@code "Bei Fertigstellung Email an " + Adresse}, und genau
 * ein Bewohner hat einen Unterstrich in seiner Adresse. Danach wird der betroffene Knoten nie
 * wieder gelayoutet ({@code Parent.performingLayout} bleibt stehen), und weil erst das Layout
 * den Text in den gezeichneten Knoten schreibt, bleibt die Anzeige stehen, während die
 * Properties weiterlaufen.
 *
 * <p>Der Test fährt genau diese Folge auf dem echten FXML: Text mit Unterstrich, Layout,
 * {@code onDeactivate()}, Layout. Gegen den Vor-Fix-Stand ist er rot.
 *
 * <p>Zwei Sicherungen werden geprüft - {@code mnemonicParsing="false"} an beiden Checkboxen
 * (nimmt dem Absturz den Pfad) und der nicht-leere Platzhalter (nimmt ihm die Bedingung).
 * Beide sind einzeln wirksam; gemessen wurde das an JavaFX 17.0.20.
 */
public class ConfirmationPaneLayoutTest extends ApplicationTest {

    private static final String FXML =
            "/org/kabieror/elwasys/raspiclient/ui/medium/components/ConfirmationPane.fxml";

    private static final String ADDRESS_WITH_UNDERSCORE = "Bei Fertigstellung Email an max_muster@example.org";

    private Parent root;
    private ConfirmationViewController controller;

    @Override
    public void start(Stage stage) throws Exception {
        final FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML));
        this.root = loader.load();
        this.controller = loader.getController();
        // false: überspringt jede Berührung von ElwaManager.instance, siehe MainFormStateManagerTest.
        this.controller.onStart(new MainFormController(false));
        stage.setScene(new Scene(this.root, 800, 480));
        stage.show();
    }

    @Test
    void bothCheckBoxesHaveMnemonicParsingOff() {
        assertFalse(checkBox("#emailNotificationCheckBox").isMnemonicParsing(),
                "mnemonic parsing turns an underscore in the mail address into a keyboard shortcut - "
                        + "it swallows the character and crashes the layout pass");
        assertFalse(checkBox("#ionicNotificationCheckBox").isMnemonicParsing(),
                "same hazard, same page");
    }

    @Test
    void aMailAddressWithAnUnderscoreSurvivesTheLayoutPassAndStaysReadable() {
        final CheckBox box = checkBox("#emailNotificationCheckBox");
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        interact(() -> {
            try {
                this.controller.setEmailNotificationText(ADDRESS_WITH_UNDERSCORE);
                layoutFirstTime();
                // Wie im Betrieb: der Wechsel zurück zur Geräteliste leert den Text.
                this.controller.onDeactivate();
                layoutNow();
                // Und danach muss das Layout weiterarbeiten - sonst steht die Anzeige.
                this.controller.setTitleText("Titel nach dem Abmelden");
                layoutNow();
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        assertNull(failure.get(), "the layout pass must survive a mail address with an underscore");
        assertFalse(this.root.isNeedsLayout(), "the layout pass must not be stuck afterwards");
        assertEquals("Titel nach dem Abmelden", renderedTextOf("#confirmationPane .title"),
                "a text set after the crashing sequence must still reach the rendered node");
        assertEquals(ADDRESS_WITH_UNDERSCORE, renderedBefore(box),
                "the underscore must not be swallowed as a mnemonic while it is displayed");
    }

    /**
     * Misst die beiden Bedingungen des Absturzes an einem nackten Bedienelement - das ist der
     * Beweis, dass die Sicherungen dieser Seite etwas tragen, und zugleich die Fundstelle für
     * den nächsten Vorfall dieser Art.
     *
     * <p>Sollte JavaFX den Fehler eines Tages beheben, schlägt der erste Teil fehl. Dann ist
     * das kein Alarm, sondern die Nachricht, dass eine der beiden Sicherungen entbehrlich wird.
     */
    @Test
    void anEmptyTextAfterAnUnderscoreBreaksTheLayoutPassButThePlaceholderDoesNot() {
        assertNotNull(layoutFailureAfter(""),
                "measured on JavaFX 17.0.20: an empty text after an underscore breaks the layout pass");
        assertNull(layoutFailureAfter(org.kabieror.elwasys.raspiclient.ui.UiUtilities.BLANK_LABEL_TEXT),
                "the placeholder must take that condition away - that is what it is for");
    }

    /**
     * Baut ein Bedienelement mit Mnemonic-Parsing (der Standard von {@code CheckBox}), gibt ihm
     * einen Text mit Unterstrich, danach den übergebenen Text - und meldet, ob der
     * Layout-Durchlauf das überlebt.
     */
    private Throwable layoutFailureAfter(String secondText) {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        interact(() -> {
            final CheckBox probe = new CheckBox();
            probe.setMnemonicParsing(true);
            final javafx.scene.layout.VBox host = new javafx.scene.layout.VBox(probe);
            new Scene(host, 300, 100);
            try {
                probe.setText(ADDRESS_WITH_UNDERSCORE);
                host.applyCss();
                host.layout();
                probe.setText(secondText);
                host.layout();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        return failure.get();
    }

    /**
     * Ein Layout-Durchlauf wie ihn der JavaFX-Puls auslöst - ausdrücklich, damit eine Ausnahme
     * hier im Test landet statt im Puls-Thread (wo sie nur geloggt würde).
     */
    private void layoutNow() {
        this.root.layout();
    }

    /** Erster Durchlauf: einmal CSS anwenden, damit die Skins stehen. */
    private void layoutFirstTime() {
        this.root.applyCss();
        this.root.layout();
    }

    private CheckBox checkBox(String selector) {
        return (CheckBox) this.root.lookup(selector);
    }

    /** Der gezeichnete Text steckt im Text-Knoten der Skin, nicht in der Property. */
    private String renderedTextOf(String selector) {
        final Node node = this.root.lookup(selector);
        final Text text = node == null ? null : (Text) node.lookup(".text");
        return text == null ? null : text.getText();
    }

    private String renderedBefore(CheckBox box) {
        // Nach onDeactivate trägt die Checkbox den Platzhalter; für die Lesbarkeit der Adresse
        // wird sie hier noch einmal gesetzt und gelayoutet.
        final AtomicReference<String> rendered = new AtomicReference<>();
        interact(() -> {
            this.controller.setEmailNotificationText(ADDRESS_WITH_UNDERSCORE);
            layoutFirstTime();
            final Text text = (Text) box.lookup(".text");
            rendered.set(text == null ? null : text.getText());
        });
        return rendered.get();
    }
}
