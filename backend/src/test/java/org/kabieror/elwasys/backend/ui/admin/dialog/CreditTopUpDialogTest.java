package org.kabieror.elwasys.backend.ui.admin.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.CreditService;

/**
 * Regressionstest zum Doppelklick-Schutz des "Buchen"-Knopfs (Issue #49): nach einer
 * FEHLGESCHLAGENEN Validierung muss er bedienbar bleiben.
 *
 * <p>Der Dialog bleibt in diesem Fall offen - der Administrator soll den Betrag korrigieren und
 * erneut absenden. Vaadins {@code setDisableOnClick} deaktiviert die Schaltfläche jedoch nur und
 * aktiviert sie NIE von allein (siehe {@code PortalButtons}); ohne das Zurücksetzen war der
 * einzige Weg aus dem Dialog heraus "Abbrechen" und von vorn.
 */
class CreditTopUpDialogTest {

    private CreditService creditService;

    /** Starke Referenz nötig - siehe {@code TerminalTokenDialogTest}. */
    private UI ui;

    @BeforeEach
    void setUp() {
        this.ui = new UI();
        UI.setCurrent(this.ui);
        this.creditService = mock(CreditService.class);
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void bookingStaysClickableAfterAFailedValidation() {
        CreditTopUpDialog dialog = new CreditTopUpDialog(this.creditService,
                new UserEntity("Erika Musterfrau", "erika", null), "admin", () -> {
                });
        dialog.open();
        Button btnBook = DialogComponents.button(dialog, "Buchen");

        // Ohne Betrag: die Feldprüfung greift, es wird nichts gebucht und der Dialog bleibt offen.
        btnBook.click();

        verifyNoInteractions(this.creditService);
        assertThat(dialog.isOpened()).as("Vorbedingung: der Dialog bleibt zum Korrigieren offen").isTrue();
        assertThat(btnBook.isEnabled())
                .as("nach der abgelehnten Eingabe muss sich der Betrag korrigieren und erneut absenden lassen")
                .isTrue();
    }
}
