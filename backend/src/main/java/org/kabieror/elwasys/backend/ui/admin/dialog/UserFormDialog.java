package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.DuplicateCardIdException;
import org.kabieror.elwasys.backend.exception.DuplicateUsernameException;
import org.kabieror.elwasys.backend.service.PasswordResetService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.service.UserService;

/**
 * Modaler Dialog zum Anlegen/Bearbeiten eines Benutzers - fachlicher Nachfolger von
 * {@code Portal/.../components/UserWindow} (Alt-Portal, Testfälle P6/P7). Die Felder
 * entsprechen 1:1 dem Alt-Fenster: Name, Username, Email, Kartennummern, Benutzergruppe,
 * Gesperrt. <b>Seit Phase 3 AP4</b> zusätzlich der Admin-Passwort-Reset-Teil des Alt-Fensters
 * ("Sende dem Benutzer per Email ein neues Passwort", Checkbox {@code cbSendPassword}), der in
 * AP2 bewusst ausgespart worden war - siehe {@link PasswordResetService#resetPasswordByAdminAndNotify}.
 */
public class UserFormDialog extends Dialog {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final UserEntity userToEdit;

    private final TextField tfName = new TextField("Name");
    private final TextField tfUsername = new TextField("Username");
    private final TextField tfEmail = new TextField("Email");
    private final TextArea tfCardIds = new TextArea("Kartennummern");
    private final ComboBox<UserGroupEntity> cbUserGroup = new ComboBox<>("Benutzergruppe");
    private final Checkbox cbBlocked = new Checkbox("Gesperrt");
    private final Checkbox cbSendPassword = new Checkbox("Sende dem Benutzer per Email ein neues Passwort");

    public UserFormDialog(UserService userService, UserGroupService userGroupService,
            PasswordResetService passwordResetService, UserEntity userToEdit, Runnable onSaved) {
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.userToEdit = userToEdit;

        boolean editMode = userToEdit != null;
        setHeaderTitle(editMode ? "Benutzer bearbeiten" : "Benutzer erstellen");
        setModal(true);
        setWidth("35em");

        this.tfName.setRequired(true);
        this.tfName.setMaxLength(50);
        this.tfName.setWidthFull();

        this.tfUsername.setRequired(true);
        // Das Datenbankfeld hat die maximale Länge 50. Beim Löschen wird ein Präfix
        // "#del<id>#" (bis zu 10 Zeichen) vorangestellt - siehe UserService#delete.
        this.tfUsername.setMaxLength(40);
        this.tfUsername.setWidthFull();

        this.tfEmail.setWidthFull();
        this.tfEmail.setMaxLength(50);
        this.tfEmail.setHelperText("Optional");

        this.tfCardIds.setHelperText("Die Kartennummern, die dem Benutzer zugeordnet sind. Eine Nummer pro Zeile.");
        this.tfCardIds.setWidthFull();

        this.cbUserGroup.setRequired(true);
        this.cbUserGroup.setItems(userGroupService.findAll());
        this.cbUserGroup.setItemLabelGenerator(UserGroupEntity::getName);
        this.cbUserGroup.setWidthFull();

        // 1:1 wie UserWindow: bei "Benutzer erstellen" standardmäßig angehakt (ein neuer
        // Benutzer braucht ein initiales Passwort), bei "Benutzer bearbeiten" standardmäßig
        // aus.
        this.cbSendPassword.setValue(!editMode);

        FormLayout form = new FormLayout(this.tfName, this.tfUsername, this.tfEmail, this.tfCardIds,
                this.cbUserGroup, this.cbBlocked, this.cbSendPassword);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        if (editMode) {
            this.tfName.setValue(userToEdit.getName());
            this.tfUsername.setValue(userToEdit.getUsername());
            this.tfEmail.setValue(userToEdit.getEmail() == null ? "" : userToEdit.getEmail());
            this.tfCardIds.setValue(String.join("\n", filterEmpty(userToEdit.getCardIds())));
            this.cbUserGroup.setValue(userToEdit.getGroup());
            this.cbBlocked.setValue(userToEdit.isBlocked());
        }

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button(editMode ? "Speichern" : "Erstellen", e -> save(onSaved));
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout footer = new HorizontalLayout(btnCancel, btnSave);
        getFooter().add(footer);
    }

    private static String[] filterEmpty(String[] values) {
        return java.util.Arrays.stream(values).filter(v -> v != null && !v.isEmpty()).toArray(String[]::new);
    }

    private void save(Runnable onSaved) {
        boolean valid = true;

        if (this.tfName.isEmpty()) {
            this.tfName.setInvalid(true);
            this.tfName.setErrorMessage("Bitte Name eingeben.");
            valid = false;
        } else {
            this.tfName.setInvalid(false);
        }

        if (this.tfUsername.isEmpty()) {
            this.tfUsername.setInvalid(true);
            this.tfUsername.setErrorMessage("Bitte Benutzernamen eingeben.");
            valid = false;
        } else {
            this.tfUsername.setInvalid(false);
        }

        String email = this.tfEmail.getValue();
        // 1:1 wie UserWindow#save (tfEmail.setRequired(cbSendPassword.getValue())): ist ein
        // neues Passwort per Email zu versenden, wird zwingend eine Adresse benötigt.
        if (this.cbSendPassword.getValue() && (email == null || email.isBlank())) {
            this.tfEmail.setInvalid(true);
            this.tfEmail.setErrorMessage("Für das Zusenden eines Passworts wird eine Email-Adresse benötigt.");
            valid = false;
        } else if (email != null && !email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            this.tfEmail.setInvalid(true);
            this.tfEmail.setErrorMessage("Das ist keine gültige Email-Adresse");
            valid = false;
        } else {
            this.tfEmail.setInvalid(false);
        }

        String[] cardIds = splitCardIds(this.tfCardIds.getValue());
        for (String cardId : cardIds) {
            if (!cardId.matches("^\\d+$")) {
                this.tfCardIds.setInvalid(true);
                this.tfCardIds.setErrorMessage("Die Kartennummer '" + cardId + "' ist ungültig.");
                valid = false;
                break;
            }
            this.tfCardIds.setInvalid(false);
        }

        if (this.cbUserGroup.isEmpty()) {
            this.cbUserGroup.setInvalid(true);
            this.cbUserGroup.setErrorMessage("Bitte Benutzergruppe auswählen");
            valid = false;
        } else {
            this.cbUserGroup.setInvalid(false);
        }

        if (!valid) {
            return;
        }

        UserEntity savedUser;
        try {
            if (this.userToEdit == null) {
                savedUser = this.userService.create(this.tfName.getValue(), this.tfUsername.getValue(),
                        emptyToNull(email), cardIds, this.cbBlocked.getValue(), this.cbUserGroup.getValue());
            } else {
                savedUser = this.userService.update(this.userToEdit, this.tfName.getValue(),
                        this.tfUsername.getValue(), emptyToNull(email), cardIds, this.cbBlocked.getValue(),
                        this.cbUserGroup.getValue());
            }
        } catch (DuplicateUsernameException e) {
            this.tfUsername.setInvalid(true);
            this.tfUsername.setErrorMessage(e.getMessage());
            return;
        } catch (DuplicateCardIdException e) {
            this.tfCardIds.setInvalid(true);
            this.tfCardIds.setErrorMessage(e.getMessage());
            return;
        } catch (RuntimeException e) {
            showError("Der Benutzer konnte nicht gespeichert werden. " + e.getMessage());
            return;
        }

        // 1:1 wie UserWindow#save (Zweig cbSendPassword): NACH dem Speichern, damit ein neu
        // angelegter Benutzer bereits eine gültige Id/gespeicherte Entity hat.
        if (this.cbSendPassword.getValue()) {
            try {
                this.passwordResetService.resetPasswordByAdminAndNotify(savedUser);
                showSuccess("Passwort wurde versandt");
            } catch (RuntimeException e) {
                showError("Konnte keine Email senden. " + e.getMessage());
                // Speichern war bereits erfolgreich - Dialog trotzdem schließen, 1:1 wie im
                // Alt-Code (UserWindow#save fängt die EmailException NACH dem Speichern ab,
                // schließt das Fenster aber in jedem Fall).
            }
        }

        close();
        onSaved.run();
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private static String[] splitCardIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(raw.split("\n+")).map(String::trim).filter(v -> !v.isEmpty())
                .toArray(String[]::new);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
