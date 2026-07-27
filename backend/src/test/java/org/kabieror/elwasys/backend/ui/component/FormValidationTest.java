package org.kabieror.elwasys.backend.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.Test;

/**
 * Reiner Unit-Test der gemeinsamen Feldprüfungen (finale Review R3c, Issue #92) - kein Spring,
 * keine DB, keine UI-Session: die Vaadin-Felder sind serverseitige Objekte, ihre
 * Fehlermarkierung lässt sich ohne laufende UI prüfen.
 *
 * <p>Warum überhaupt getestet: {@link FormValidation} ersetzt die zuvor in jedem Dialog einzeln
 * ausgeschriebenen {@code require*}-Helfer. Genau die zwei Feinheiten, auf die sich die Dialoge
 * verlassen, sichert dieser Test ab - eine bestandene Prüfung LÖSCHT die Markierung wieder (sonst
 * bliebe ein korrigiertes Feld rot), und {@code check} liefert sein Ergebnis zurück, damit die
 * Dialoge mit {@code valid &= ...} alle Felder prüfen können statt nur bis zum ersten Fehler.
 */
class FormValidationTest {

    @Test
    void requireMarksAnEmptyFieldAndReportsTheFailure() {
        TextField field = new TextField();

        boolean valid = FormValidation.require(field, "Bitte Name eingeben.");

        assertThat(valid).isFalse();
        assertThat(field.isInvalid()).isTrue();
        assertThat(field.getErrorMessage()).isEqualTo("Bitte Name eingeben.");
    }

    @Test
    void requireClearsTheMarkOnceTheFieldIsFilled() {
        TextField field = new TextField();
        FormValidation.require(field, "Bitte Name eingeben.");

        field.setValue("Waschmaschine 1");
        boolean valid = FormValidation.require(field, "Bitte Name eingeben.");

        assertThat(valid).isTrue();
        assertThat(field.isInvalid()).isFalse();
    }

    @Test
    void requireTreatsAnUnselectedComboBoxAsEmpty() {
        ComboBox<String> combo = new ComboBox<>();
        combo.setItems("Default");

        assertThat(FormValidation.require(combo, "Bitte Standort auswählen.")).isFalse();
        assertThat(combo.isInvalid()).isTrue();

        combo.setValue("Default");

        assertThat(FormValidation.require(combo, "Bitte Standort auswählen.")).isTrue();
        assertThat(combo.isInvalid()).isFalse();
    }

    @Test
    void checkMarksTheFieldExactlyWhenTheConditionFails() {
        TextField field = new TextField();

        assertThat(FormValidation.check(false, field, "Der Betrag muss größer als 0 sein.")).isFalse();
        assertThat(field.isInvalid()).isTrue();
        assertThat(field.getErrorMessage()).isEqualTo("Der Betrag muss größer als 0 sein.");

        assertThat(FormValidation.check(true, field, "Der Betrag muss größer als 0 sein.")).isTrue();
        assertThat(field.isInvalid()).isFalse();
    }

    @Test
    void rejectMarksAFieldWhoseFailureIsAlreadyKnown() {
        // Der Service-Fehlerpfad der Dialoge (doppelter Benutzername, doppelte Kartennummer):
        // der Fehler steht fest, es gibt keine Bedingung mehr zu prüfen.
        TextField field = new TextField("Username");
        field.setValue("erika");

        assertThat(FormValidation.reject(field, "Dieser Benutzername ist bereits vergeben.")).isFalse();
        assertThat(field.isInvalid()).isTrue();
        assertThat(field.getErrorMessage()).isEqualTo("Dieser Benutzername ist bereits vergeben.");
    }

    @Test
    void isValidEmailKeepsTheLenientRuleOfTheLegacyPortal() {
        assertThat(FormValidation.isValidEmail("max.mustermann@example.com")).isTrue();
        // Bewusst nachsichtig: keine Prüfung der TLD, nur "etwas@etwas.etwas" ohne Leerzeichen.
        assertThat(FormValidation.isValidEmail("a@b.c")).isTrue();

        assertThat(FormValidation.isValidEmail("max@example")).isFalse();
        assertThat(FormValidation.isValidEmail("max example@test.de")).isFalse();
        assertThat(FormValidation.isValidEmail("max@@example.de")).isFalse();
        assertThat(FormValidation.isValidEmail("")).isFalse();
        assertThat(FormValidation.isValidEmail(null)).isFalse();
    }
}
