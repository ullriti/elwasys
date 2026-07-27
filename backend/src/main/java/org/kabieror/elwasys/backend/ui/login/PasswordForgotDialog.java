package org.kabieror.elwasys.backend.ui.login;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import org.kabieror.elwasys.backend.service.PasswordResetService;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.Notifications;

/**
 * Dialog "Passwort zurücksetzen" (Phase 3 AP4, Testfall P19) - fachlicher Nachfolger von
 * {@code Portal/.../components/PasswordForgotWindow} (Alt-Portal). Erreichbar über den
 * "Passwort vergessen?"-Knopf der {@link LoginView} (bis AP4 deaktiviert, siehe
 * {@code LoginForm#setForgotPasswordButtonVisible}).
 *
 * <p>P19 verlangt laut docs/kb/08-test-plan.md nur, dass sich dieser Dialog öffnet (kein echter
 * Mailversand im Test) - die Absende-Logik selbst wird service-seitig getestet
 * ({@code PasswordResetServiceTest}).
 */
public class PasswordForgotDialog extends AbstractFormDialog {

    private final PasswordResetService passwordResetService;

    private final EmailField tfEmail = new EmailField("Email");

    public PasswordForgotDialog(PasswordResetService passwordResetService) {
        super("Passwort zurücksetzen", "22em");
        this.passwordResetService = passwordResetService;

        Paragraph explanation = new Paragraph(
                "Bitte gib hier deine Email-Adresse ein. Falls ein Konto zu dieser Adresse existiert, erhältst du "
                        + "einen Link, mit welchem du ein neues Passwort setzen kannst.");

        this.tfEmail.setRequired(true);
        this.tfEmail.setWidthFull();
        this.tfEmail.setErrorMessage("Bitte gültige Email-Adresse eingeben.");

        VerticalLayout content = new VerticalLayout(explanation, this.tfEmail);
        content.setPadding(false);
        addToBody(content);

        addFooterActions("OK", this::execute);

        focusOnOpen(this.tfEmail);
    }

    private void execute() {
        if (this.tfEmail.isEmpty() || this.tfEmail.isInvalid()) {
            this.tfEmail.setInvalid(true);
            return;
        }
        this.tfEmail.setInvalid(false);

        try {
            this.passwordResetService.requestReset(this.tfEmail.getValue());
        } catch (RuntimeException e) {
            // Ohne Detailtext: dieser Dialog steht VOR dem Login (siehe AbstractFormDialog).
            showFailure("Konnte die Anfrage nicht verarbeiten.", e, false);
            return;
        }

        close();
        // Neutrale Meldung (Issue #24, ADR 0018): IMMER dieselbe Rückmeldung, unabhängig
        // davon, ob zu der Adresse ein Konto existiert - so verrät der Dialog die
        // Kontenexistenz nicht.
        Notifications.showSuccess("Falls ein Konto zu dieser Adresse existiert, wurde eine Email versandt.");
    }


}
