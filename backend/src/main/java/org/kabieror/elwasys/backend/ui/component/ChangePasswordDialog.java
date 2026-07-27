package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.exception.InvalidCurrentPasswordException;
import org.kabieror.elwasys.backend.exception.PasswordTooShortException;
import org.kabieror.elwasys.backend.service.PasswordService;

/**
 * Dialog "Passwort ändern" für den angemeldeten Benutzer (Phase 3 AP4, Testfall P16) -
 * fachlicher Nachfolger von {@code Portal/.../components/ChangePasswordWindow} (Alt-Portal).
 * Felder/Reihenfolge 1:1 wie im Alt-Fenster: Altes Passwort, Neues Passwort, Wiederholung.
 */
public class ChangePasswordDialog extends AbstractFormDialog {

    private final PasswordService passwordService;
    private final UserEntity user;

    private final PasswordField tfOldPassword = new PasswordField("Altes Passwort");
    private final PasswordField tfNewPassword1 = new PasswordField("Neues Passwort");
    private final PasswordField tfNewPassword2 = new PasswordField("Wiederholung");

    public ChangePasswordDialog(PasswordService passwordService, UserEntity user, Runnable onSaved) {
        super("Passwort ändern - " + user.getName(), "22em");
        this.passwordService = passwordService;
        this.user = user;

        this.tfOldPassword.setRequired(true);
        this.tfOldPassword.setWidthFull();

        this.tfNewPassword1.setRequired(true);
        this.tfNewPassword1.setMaxLength(50);
        this.tfNewPassword1.setMinLength(PasswordService.MIN_PASSWORD_LENGTH);
        this.tfNewPassword1.setHelperText("Mindestens " + PasswordService.MIN_PASSWORD_LENGTH + " Zeichen.");
        this.tfNewPassword1.setWidthFull();

        this.tfNewPassword2.setRequired(true);
        this.tfNewPassword2.setMaxLength(50);
        this.tfNewPassword2.setWidthFull();

        FormLayout form = new FormLayout(this.tfOldPassword, this.tfNewPassword1, this.tfNewPassword2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        addFooterActions("OK", () -> save(onSaved));

        focusOnOpen(this.tfOldPassword);
    }

    private void save(Runnable onSaved) {
        // Bewusst bitweises "&=" statt des kurzschließenden "&&": ALLE Felder sollen geprüft und
        // markiert werden, nicht nur bis zum ersten Fehler.
        boolean valid = FormValidation.require(this.tfOldPassword, "Bitte altes Passwort eingeben.");
        valid &= FormValidation.require(this.tfNewPassword1, "Bitte neues Passwort eingeben.");
        // Hier dagegen kurzschließend: auf Übereinstimmung wird erst geprüft, wenn die
        // Wiederholung überhaupt ausgefüllt ist - sonst stünde am leeren Feld die falsche
        // Meldung.
        valid &= FormValidation.require(this.tfNewPassword2, "Bitte neues Passwort wiederholen.")
                && FormValidation.check(this.tfNewPassword2.getValue().equals(this.tfNewPassword1.getValue()),
                        this.tfNewPassword2, "Die Passwörter stimmen nicht überein.");

        if (!valid) {
            return;
        }

        try {
            this.passwordService.changeOwnPassword(this.user, this.tfOldPassword.getValue(),
                    this.tfNewPassword1.getValue());
        } catch (InvalidCurrentPasswordException e) {
            FormValidation.reject(this.tfOldPassword, "Das Passwort ist nicht korrekt.");
            return;
        } catch (PasswordTooShortException e) {
            FormValidation.reject(this.tfNewPassword1, e.getMessage());
            return;
        }

        close();
        onSaved.run();
        Notifications.showSuccess("Passwort wurde erfolgreich geändert.");
    }

}
