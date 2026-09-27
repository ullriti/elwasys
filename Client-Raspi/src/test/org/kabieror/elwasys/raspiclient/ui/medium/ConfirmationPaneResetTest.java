package org.kabieror.elwasys.raspiclient.ui.medium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.ui.UiUtilities;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.ConfirmationViewController;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Regressionstest zum Vorfall am Terminal Hilarenhaus vom 2026-09-24.
 *
 * <p>Den Layout-Absturz selbst deckt {@link ConfirmationPaneLayoutTest} ab. Hier geht es um
 * die zweite Hälfte desselben Befunds: beim Abmelden blieben alle benutzerbezogenen Werte in
 * den Properties stehen - Benutzername, Guthaben, Preis, Restguthaben und die E-Mail-Adresse
 * des vorigen Benutzers. Sichtbar ist dieses Fenster heute nicht (der Detailbereich hängt an
 * der Style-Klasse {@code program-selected}), aber Werte eines anderen Benutzers haben in
 * einem Zustand, der sie nicht zeigen soll, nichts verloren - erst recht nicht auf einer
 * Seite, die genau damit aufgefallen ist.
 *
 * <p>Der Test prüft beides am echten FXML: nach dem Abmelden steht kein Wert des vorigen
 * Benutzers mehr da, und keine Beschriftung ist leer (die zweite Sicherung gegen den
 * Absturzpfad, siehe {@code UiUtilities#setLabelText}).
 *
 * <p>Liegt bewusst im Package von {@link MainFormController}: {@code onDeactivate()} meldet
 * sich vom {@code registeredUser}-Property ab und braucht deshalb einen Controller. Der
 * package-private Test-Konstruktor {@code new MainFormController(false)} liefert ihn, ohne
 * den {@code ElwaManager}-Singleton anzufassen (siehe {@code MainFormStateManagerTest}).
 */
public class ConfirmationPaneResetTest extends ApplicationTest {

    private static final String FXML =
            "/org/kabieror/elwasys/raspiclient/ui/medium/components/ConfirmationPane.fxml";

    private ConfirmationViewController controller;

    @Override
    public void start(Stage stage) throws Exception {
        final FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML));
        final Parent root = loader.load();
        this.controller = loader.getController();
        // false: überspringt jede Berührung von ElwaManager.instance, siehe Klassen-Javadoc.
        this.controller.onStart(new MainFormController(false));
        stage.setScene(new Scene(root, 800, 480));
        stage.show();
    }

    @Test
    void aFreshPaneHasNoEmptyLabelText() {
        // Der Push-Text wird seit dem Entfernen der elwaApp-Kopplung nirgends mehr gesetzt -
        // ohne Startwert wäre er null, und null zählt für den Absturzpfad wie leer.
        assertLabelTextIsNeverEmpty();
    }

    @Test
    void deactivationLeavesNoDataOfThePreviousUserBehind() {
        interact(() -> {
            this.controller.setRegisteredUserUserName("vorherigerNutzer");
            this.controller.setUserCredit("35,80 €");
            this.controller.setRemainingCredit("34,60 €");
            this.controller.setMaxPrice("1,20 €");
            this.controller.setTitleText("Normalwäsche auf Maschine 1");
            this.controller.setLatestEnd("24.09.2026, 21:30:00");
            this.controller.setEmailNotificationText("Bei Fertigstellung Email an wer@example.org");

            this.controller.onDeactivate();
        });

        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getRegisteredUserUserName(),
                "the user name of the previous user must not survive the logout");
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getUserCredit(),
                "the credit of the previous user must not survive the logout");
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getRemainingCredit());
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getMaxPrice());
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getTitleText());
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getLatestEnd());
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, this.controller.getEmailNotificationText(),
                "the mail address of the previous user must not survive the logout");
    }

    @Test
    void deactivationLeavesNoEmptyLabelTextBehind() {
        interact(this.controller::onDeactivate);

        assertLabelTextIsNeverEmpty();
    }

    /**
     * Prüft die eigentliche Absturzbedingung: keine der Beschriftungen dieser Seite darf
     * {@code null} oder leer sein.
     */
    private void assertLabelTextIsNeverEmpty() {
        assertLabelTextIsNeverEmpty("titleText", this.controller.getTitleText());
        assertLabelTextIsNeverEmpty("latestEnd", this.controller.getLatestEnd());
        assertLabelTextIsNeverEmpty("userCredit", this.controller.getUserCredit());
        assertLabelTextIsNeverEmpty("maxPrice", this.controller.getMaxPrice());
        assertLabelTextIsNeverEmpty("remainingCredit", this.controller.getRemainingCredit());
        assertLabelTextIsNeverEmpty("emailNotificationText", this.controller.getEmailNotificationText());
        assertLabelTextIsNeverEmpty("ionicNotificationText", this.controller.getIonicNotificationText());
        assertLabelTextIsNeverEmpty("registeredUserUserName", this.controller.getRegisteredUserUserName());
        assertLabelTextIsNeverEmpty("moreInfoText", this.controller.getMoreInfoText());
    }

    private static void assertLabelTextIsNeverEmpty(String name, String value) {
        assertTrue(value != null && !value.isEmpty(),
                "label text '" + name + "' must never be null or empty - JavaFX crashes its layout pass on it");
    }
}
