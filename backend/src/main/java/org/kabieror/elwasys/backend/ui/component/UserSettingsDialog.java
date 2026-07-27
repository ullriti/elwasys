package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
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
public class UserSettingsDialog extends AbstractFormDialog {

    private final UserService userService;
    private final UserEntity user;

    private final TextField tfEmail = new TextField("Email");
    private final Checkbox cbEmailNotification = new Checkbox("Email-Benachrichtigung");
    private final TextField tfPushoverKey = new TextField("Pushover-Key");

    public UserSettingsDialog(UserService userService, UserEntity user, Runnable onSaved) {
        super("Benutzer ändern - " + user.getName(), "35em");
        this.userService = userService;
        this.user = user;

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

        addFooterActions("OK", () -> save(onSaved));
    }

    private void save(Runnable onSaved) {
        String email = this.tfEmail.getValue();
        boolean emailRequired = this.cbEmailNotification.getValue();

        if (emailRequired && (email == null || email.isBlank())) {
            FormValidation.reject(this.tfEmail, "Für Benachrichtigungen wird eine Email-Adresse benötigt.");
            return;
        }
        // Eine leere Adresse ist erlaubt (das Feld ist optional), eine gefüllte muss passen.
        if (!FormValidation.check(email == null || email.isBlank() || FormValidation.isValidEmail(email),
                this.tfEmail, "Dies ist keine gültige Email-Adresse.")) {
            return;
        }

        String pushoverKey = this.tfPushoverKey.getValue();
        if (!FormValidation.check(
                pushoverKey == null || pushoverKey.isEmpty() || pushoverKey.matches("[a-zA-Z0-9]+"),
                this.tfPushoverKey, "Der Schlüssel muss aus Zahlen und Buchstaben bestehen.")) {
            return;
        }

        this.userService.updateOwnSettings(this.user, email, emailRequired, pushoverKey);

        close();
        onSaved.run();
    }
}
