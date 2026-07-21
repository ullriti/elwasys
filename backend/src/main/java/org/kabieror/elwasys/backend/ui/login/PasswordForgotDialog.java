package org.kabieror.elwasys.backend.ui.login;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import org.kabieror.elwasys.backend.service.PasswordResetService;
import org.kabieror.elwasys.backend.service.PasswordResetService.UserNotFoundForEmailException;

/**
 * Dialog "Passwort zurücksetzen" (Phase 3 AP4, Testfall P19) - fachlicher Nachfolger von
 * {@code Portal/.../components/PasswordForgotWindow} (Alt-Portal). Erreichbar über den
 * "Passwort vergessen?"-Knopf der {@link LoginView} (bis AP4 deaktiviert, siehe
 * {@code LoginForm#setForgotPasswordButtonVisible}).
 *
 * <p>P19 verlangt laut kb/08-test-plan.md nur, dass sich dieser Dialog öffnet (kein echter
 * Mailversand im Test) - die Absende-Logik selbst wird service-seitig getestet
 * ({@code PasswordResetServiceTest}).
 */
public class PasswordForgotDialog extends Dialog {

    private final PasswordResetService passwordResetService;

    private final EmailField tfEmail = new EmailField("Email");

    public PasswordForgotDialog(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;

        setHeaderTitle("Passwort zurücksetzen");
        setModal(true);
        setWidth("22em");

        Paragraph explanation = new Paragraph(
                "Bitte gib hier deine Email-Adresse ein. Du wirst einen Link erhalten, mit welchem du ein neues "
                        + "Passwort setzen kannst.");

        this.tfEmail.setRequired(true);
        this.tfEmail.setWidthFull();
        this.tfEmail.setErrorMessage("Bitte gültige Email-Adresse eingeben.");

        VerticalLayout content = new VerticalLayout(explanation, this.tfEmail);
        content.setPadding(false);
        add(content);

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button("OK", e -> execute());
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSave));

        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                this.tfEmail.focus();
            }
        });
    }

    private void execute() {
        if (this.tfEmail.isEmpty() || this.tfEmail.isInvalid()) {
            this.tfEmail.setInvalid(true);
            return;
        }
        this.tfEmail.setInvalid(false);

        try {
            this.passwordResetService.requestReset(this.tfEmail.getValue());
        } catch (UserNotFoundForEmailException e) {
            showError("Es konnte kein Benutzer mit der angegebenen Email-Adresse gefunden werden.");
            return;
        } catch (RuntimeException e) {
            showError("Konnte die Email nicht senden. " + e.getMessage());
            return;
        }

        close();
        showSuccess("Die Email wurde versandt. Prüfe dein Postfach!");
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
