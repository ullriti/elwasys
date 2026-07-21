package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.exception.InvalidCurrentPasswordException;
import org.kabieror.elwasys.backend.service.PasswordService;

/**
 * Dialog "Passwort ändern" für den angemeldeten Benutzer (Phase 3 AP4, Testfall P16) -
 * fachlicher Nachfolger von {@code Portal/.../components/ChangePasswordWindow} (Alt-Portal).
 * Felder/Reihenfolge 1:1 wie im Alt-Fenster: Altes Passwort, Neues Passwort, Wiederholung.
 */
public class ChangePasswordDialog extends Dialog {

    private final PasswordService passwordService;
    private final UserEntity user;

    private final PasswordField tfOldPassword = new PasswordField("Altes Passwort");
    private final PasswordField tfNewPassword1 = new PasswordField("Neues Passwort");
    private final PasswordField tfNewPassword2 = new PasswordField("Wiederholung");

    public ChangePasswordDialog(PasswordService passwordService, UserEntity user, Runnable onSaved) {
        this.passwordService = passwordService;
        this.user = user;

        setHeaderTitle("Passwort ändern - " + user.getName());
        setModal(true);
        setWidth("22em");

        this.tfOldPassword.setRequired(true);
        this.tfOldPassword.setWidthFull();

        this.tfNewPassword1.setRequired(true);
        this.tfNewPassword1.setMaxLength(50);
        this.tfNewPassword1.setWidthFull();

        this.tfNewPassword2.setRequired(true);
        this.tfNewPassword2.setMaxLength(50);
        this.tfNewPassword2.setWidthFull();

        FormLayout form = new FormLayout(this.tfOldPassword, this.tfNewPassword1, this.tfNewPassword2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button("OK", e -> save(onSaved));
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSave));

        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                this.tfOldPassword.focus();
            }
        });
    }

    private void save(Runnable onSaved) {
        boolean valid = true;

        if (this.tfOldPassword.isEmpty()) {
            this.tfOldPassword.setInvalid(true);
            this.tfOldPassword.setErrorMessage("Bitte altes Passwort eingeben.");
            valid = false;
        } else {
            this.tfOldPassword.setInvalid(false);
        }

        if (this.tfNewPassword1.isEmpty()) {
            this.tfNewPassword1.setInvalid(true);
            this.tfNewPassword1.setErrorMessage("Bitte neues Passwort eingeben.");
            valid = false;
        } else {
            this.tfNewPassword1.setInvalid(false);
        }

        if (this.tfNewPassword2.isEmpty()) {
            this.tfNewPassword2.setInvalid(true);
            this.tfNewPassword2.setErrorMessage("Bitte neues Passwort wiederholen.");
            valid = false;
        } else if (!this.tfNewPassword2.getValue().equals(this.tfNewPassword1.getValue())) {
            this.tfNewPassword2.setInvalid(true);
            this.tfNewPassword2.setErrorMessage("Die Passwörter stimmen nicht überein.");
            valid = false;
        } else {
            this.tfNewPassword2.setInvalid(false);
        }

        if (!valid) {
            return;
        }

        try {
            this.passwordService.changeOwnPassword(this.user, this.tfOldPassword.getValue(),
                    this.tfNewPassword1.getValue());
        } catch (InvalidCurrentPasswordException e) {
            this.tfOldPassword.setInvalid(true);
            this.tfOldPassword.setErrorMessage("Das Passwort ist nicht korrekt.");
            return;
        }

        close();
        onSaved.run();
        showSuccess("Passwort wurde erfolgreich geändert.");
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
