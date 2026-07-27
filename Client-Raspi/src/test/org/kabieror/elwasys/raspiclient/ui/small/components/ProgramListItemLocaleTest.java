package org.kabieror.elwasys.raspiclient.ui.small.components;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.common.FormatUtilities;
import org.kabieror.elwasys.common.ProgramType;
import org.kabieror.elwasys.raspiclient.api.dto.ProgramDto;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.testfx.framework.junit5.ApplicationTest;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest zu Issue #100: der Preis in der Programmliste des KLEINEN Displays hing an
 * der Standard-Locale der JVM, waehrend der unmittelbar folgende Bestaetigungsdialog denselben
 * Betrag fest deutsch formatierte. Auf einem Image mit anderer Locale sah der Nutzer damit
 * zwei Schreibweisen fuer dieselbe Zahl - und zwar bei Geld.
 * <p>
 * Der Test setzt die Standard-Locale bewusst auf {@link Locale#US}: gegen den Stand vor dem Fix
 * schlaegt er fehl (die Zelle zeigte dann {@code $12.34}), mit Fix bleibt die Anzeige deutsch.
 * Das ist genau die Bedingung, die auf einem Bestandsgeraet ohne die Locale-Flags im
 * Startbefehl herrscht (siehe {@code deploy/terminal/run-sh.lib.sh}, Issue #101).
 */
public class ProgramListItemLocaleTest extends ApplicationTest {

    /** Macht das protected {@code updateItem} fuer den Test aufrufbar. */
    private static class TestableProgramListItem extends ProgramListItem {
        void render(ClientProgram program) {
            updateItem(program, false);
        }
    }

    private Locale originalDefault;

    @Override
    public void start(Stage stage) {
        // Kein Szenengraph noetig - die Zelle wird direkt instanziiert. start() muss aber
        // existieren, damit ApplicationTest den FX-Toolkit hochfaehrt (Label/ListCell
        // brauchen ihn).
    }

    @BeforeEach
    void forceNonGermanDefaultLocale() {
        originalDefault = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefault);
    }

    @Test
    void price_stays_german_even_when_the_jvm_default_locale_is_not() {
        final TestableProgramListItem cell = new TestableProgramListItem();

        cell.render(programCosting(new BigDecimal("12.34")));

        final String rendered = priceLabelOf(cell).getText();
        assertEquals(FormatUtilities.formatCurrency(new BigDecimal("12.34")), rendered,
                "die Zelle muss ueber FormatUtilities formatieren, nicht ueber die JVM-Locale");
        // Zusaetzlich gegen die konkrete Schreibweise pruefen, damit der Test nicht bloss
        // "beide Wege sind gleich" sagt: gegen den Vor-Fix-Stand stand hier "$12.34".
        assertTrue(rendered.startsWith("12,34"),
                "erwartet deutsche Schreibweise (Dezimalkomma), war: " + rendered);
        assertTrue(rendered.endsWith("€"),
                "erwartet Euro-Zeichen am Ende, war: " + rendered);
    }

    /** Holt das Preis-Label aus dem Grid, das die Zelle als Graphic setzt. */
    private static Label priceLabelOf(ProgramListItem cell) {
        final GridPane grid = (GridPane) cell.getGraphic();
        assertNotNull(grid, "die Zelle setzt ein GridPane als Graphic");
        // Bewusst ueber die Style-Klasse der Kinder statt per CSS-lookup(): die Zelle haengt
        // in keiner Scene, und ein lookup() ohne angewandtes CSS ist genau deshalb unzuverlaessig.
        return grid.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .filter(l -> l.getStyleClass().contains("price"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("kein Label mit der Style-Klasse 'price' im Grid"));
    }

    private static ClientProgram programCosting(BigDecimal priceAtMaxDuration) {
        return ClientProgram.of(new ProgramDto(1, "Waschen 60", ProgramType.FIXED,
                3600, 0, BigDecimal.ZERO, BigDecimal.ZERO, ChronoUnit.MINUTES,
                false, 0, true, priceAtMaxDuration));
    }
}
