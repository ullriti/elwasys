package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.UserService;

/**
 * Dialog "Einstellungen" für den angemeldeten Benutzer (Phase 3 AP4, Testfall P17) -
 * fachlicher Nachfolger von {@code Portal/.../components/UserSettingsWindow} (Alt-Portal).
 * Felder 1:1 wie im Alt-Fenster: Email, Email-Benachrichtigung (Checkbox), Pushover-Key -
 * siehe {@link UserService#updateOwnSettings} für das, was NICHT Teil dieses Dialogs ist
 * (Name/Username/Kartennummern/Gruppe/Admin-Flag/Gesperrt-Status).
 */
public class UserSettingsDialog extends Dialog {

    private final UserService userService;
    private final UserEntity user;

    private final TextField tfEmail = new TextField("Email");
    private final Checkbox cbEmailNotification = new Checkbox("Email-Benachrichtigung");
    private final TextField tfPushoverKey = new TextField("Pushover-Key");

    public UserSettingsDialog(UserService userService, UserEntity user, Runnable onSaved) {
        this.userService = userService;
        this.user = user;

        setHeaderTitle("Benutzer ändern - " + user.getName());
        setModal(true);
        setWidth("35em");

        this.tfEmail.setWidthFull();
        this.tfEmail.setMaxLength(50);
        this.tfEmail.setPlaceholder("Email-Adresse hier eintragen");
        this.tfEmail.setValue(user.getEmail() == null ? "" : user.getEmail());

        this.cbEmailNotification.setValue(user.isEmailNotification());
        this.cbEmailNotification.setTooltipText(
                "Sende Benachrichtigungen über abgeschlossene Waschvorgänge an meine Email-Adresse.");

        this.tfPushoverKey.setWidthFull();
        this.tfPushoverKey.setPlaceholder("Schlüssel hier eintragen");
        this.tfPushoverKey.setValue(user.getPushoverUserKey() == null ? "" : user.getPushoverUserKey());
        this.tfPushoverKey.setHelperText(
                "Trage deinen User-Key von Pushover.net hier ein, um dich per Push-Benachrichtigung über "
                        + "beendete Waschvorgänge benachrichtigen zu lassen.");

        FormLayout form = new FormLayout(this.tfEmail, this.cbEmailNotification, this.tfPushoverKey);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button("OK", e -> save(onSaved));
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSave));
    }

    private void save(Runnable onSaved) {
        String email = this.tfEmail.getValue();
        boolean emailRequired = this.cbEmailNotification.getValue();

        if (emailRequired && (email == null || email.isBlank())) {
            this.tfEmail.setInvalid(true);
            this.tfEmail.setErrorMessage("Für Benachrichtigungen wird eine Email-Adresse benötigt.");
            return;
        }
        if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            this.tfEmail.setInvalid(true);
            this.tfEmail.setErrorMessage("Dies ist keine gültige Email-Adresse.");
            return;
        }
        this.tfEmail.setInvalid(false);

        String pushoverKey = this.tfPushoverKey.getValue();
        if (pushoverKey != null && !pushoverKey.isEmpty() && !pushoverKey.matches("[a-zA-Z0-9]+")) {
            this.tfPushoverKey.setInvalid(true);
            this.tfPushoverKey.setErrorMessage("Der Schlüssel muss aus Zahlen und Buchstaben bestehen.");
            return;
        }
        this.tfPushoverKey.setInvalid(false);

        this.userService.updateOwnSettings(this.user, email, emailRequired, pushoverKey);

        close();
        onSaved.run();
    }
}
