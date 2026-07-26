package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.Arrays;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.DuplicateCardIdException;
import org.kabieror.elwasys.backend.exception.DuplicateUsernameException;
import org.kabieror.elwasys.backend.service.PasswordResetService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.FormValidation;
import org.kabieror.elwasys.backend.ui.component.Notifications;

/**
 * Modaler Dialog zum Anlegen/Bearbeiten eines Benutzers - fachlicher Nachfolger von
 * {@code Portal/.../components/UserWindow} (Alt-Portal, Testfälle P6/P7). Die Felder
 * entsprechen 1:1 dem Alt-Fenster: Name, Username, Email, Kartennummern, Benutzergruppe,
 * Gesperrt. <b>Seit Phase 3 AP4</b> zusätzlich der Admin-Passwort-Reset-Teil des Alt-Fensters
 * ("Sende dem Benutzer per Email ein neues Passwort", Checkbox {@code cbSendPassword}), der in
 * AP2 bewusst ausgespart worden war - siehe {@link PasswordResetService#resetPasswordByAdminAndNotify}.
 */
public class UserFormDialog extends AbstractFormDialog {

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
        super(entityTitle("Benutzer", userToEdit != null), "35em");
        this.userService = userService;
        this.passwordResetService = passwordResetService;
        this.userToEdit = userToEdit;

        boolean editMode = userToEdit != null;

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

        addFooterActions(saveCaption(editMode), () -> save(onSaved));
    }

    private static String[] filterEmpty(String[] values) {
        return Arrays.stream(values).filter(v -> v != null && !v.isEmpty()).toArray(String[]::new);
    }

    private void save(Runnable onSaved) {
        // Bewusst bitweises "&=" statt des kurzschließenden "&&": ALLE Felder sollen geprüft und
        // markiert werden, nicht nur bis zum ersten Fehler - sonst müsste der Administrator die
        // Fehler eines Formulars einzeln nacheinander aufdecken.
        boolean valid = FormValidation.require(this.tfName, "Bitte Name eingeben.");
        valid &= FormValidation.require(this.tfUsername, "Bitte Benutzernamen eingeben.");

        String email = this.tfEmail.getValue();
        // 1:1 wie UserWindow#save (tfEmail.setRequired(cbSendPassword.getValue())): ist ein
        // neues Passwort per Email zu versenden, wird zwingend eine Adresse benötigt.
        if (this.cbSendPassword.getValue() && (email == null || email.isBlank())) {
            valid &= FormValidation.reject(this.tfEmail,
                    "Für das Zusenden eines Passworts wird eine Email-Adresse benötigt.");
        } else {
            // Eine leere Adresse ist erlaubt (das Feld ist optional), eine gefüllte muss passen.
            valid &= FormValidation.check(email == null || email.isEmpty() || FormValidation.isValidEmail(email),
                    this.tfEmail, "Das ist keine gültige Email-Adresse");
        }

        String[] cardIds = splitCardIds(this.tfCardIds.getValue());
        for (String cardId : cardIds) {
            if (!FormValidation.check(cardId.matches("^\\d+$"), this.tfCardIds,
                    "Die Kartennummer '" + cardId + "' ist ungültig.")) {
                valid = false;
                break;
            }
        }

        valid &= FormValidation.require(this.cbUserGroup, "Bitte Benutzergruppe auswählen");

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
            FormValidation.reject(this.tfUsername, e.getMessage());
            return;
        } catch (DuplicateCardIdException e) {
            FormValidation.reject(this.tfCardIds, e.getMessage());
            return;
        } catch (RuntimeException e) {
            showFailure("Der Benutzer konnte nicht gespeichert werden.", e);
            return;
        }

        // 1:1 wie UserWindow#save (Zweig cbSendPassword): NACH dem Speichern, damit ein neu
        // angelegter Benutzer bereits eine gültige Id/gespeicherte Entity hat.
        if (this.cbSendPassword.getValue()) {
            try {
                this.passwordResetService.resetPasswordByAdminAndNotify(savedUser);
                Notifications.showSuccess("Passwort wurde versandt");
            } catch (RuntimeException e) {
                showFailure("Konnte keine Email senden.", e);
                // Speichern war bereits erfolgreich - Dialog trotzdem schließen, 1:1 wie im
                // Alt-Code (UserWindow#save fängt die EmailException NACH dem Speichern ab,
                // schließt das Fenster aber in jedem Fall).
            }
        }

        close();
        onSaved.run();
    }


    private static String[] splitCardIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(raw.split("\n+")).map(String::trim).filter(v -> !v.isEmpty())
                .toArray(String[]::new);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

}
