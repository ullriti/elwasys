package org.kabieror.elwasys.raspiclient.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Zusage von {@link UiUtilities#setLabelText}: an einer Beschriftung landet nie ein
 * leerer Text. Genau daran ist am 2026-09-24 der Layout-Durchlauf des Terminals Hilarenhaus
 * gescheitert - mit der Folge, dass die Bestätigungsseite drei Tage lang eingefroren blieb
 * (Begründung ausführlich im Javadoc der Methode).
 *
 * <p>Kommt ohne JavaFX-Toolkit aus: {@link SimpleStringProperty} braucht keinen
 * Application-Thread.
 */
class UiUtilitiesLabelTextTest {

    @Test
    void replacesNullAndEmptyWithThePlaceholder() {
        final StringProperty property = new SimpleStringProperty("alter Wert");

        UiUtilities.setLabelText(property, null);
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, property.get(), "null must become the placeholder");

        property.set("alter Wert");
        UiUtilities.setLabelText(property, "");
        assertEquals(UiUtilities.BLANK_LABEL_TEXT, property.get(), "empty must become the placeholder");
    }

    @Test
    void keepsARealTextUnchanged() {
        final StringProperty property = new SimpleStringProperty();

        UiUtilities.setLabelText(property, "Bei Fertigstellung Email an wer@example.org");

        assertEquals("Bei Fertigstellung Email an wer@example.org", property.get());
    }

    @Test
    void thePlaceholderItselfIsNotEmpty() {
        assertFalse(UiUtilities.BLANK_LABEL_TEXT.isEmpty(),
                "a placeholder that is empty itself would reintroduce the very crash");
    }
}
