package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.kabieror.elwasys.common.FormatUtilities;
import org.kabieror.elwasys.raspiclient.model.ClientUser;
import org.kabieror.elwasys.raspiclient.ui.medium.IViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;

/**
 * Controller für die Anzeige der Benutzerdaten.
 * <p>
 * Die Auth-Key-Anzeige (elwaApp-Kopplung) zeigt seit Phase 4 AP4 immer den
 * "nicht verbunden"-Hinweis - siehe {@link ConfirmationViewController} Klassenkommentar für
 * die Begründung (App-Altlast, bewusst nicht ins neue Datenmodell gemappt).
 *
 * @author Oliver Kabierschke
 */
public class UserSettingsViewController implements IViewController {

    public Node userSettingsPane;
    public Node userSettingsContent;
    public HBox authKeyInfo;
    public HBox appConnectionInfo;
    private MainFormController mainFormController;
    private ChangeListener<ClientUser> userChangedListener = (observable, oldValue, newValue) -> {
        this.mainFormController.hideUserSettings();
    };
    private ToolbarState toolbarState =
            new ToolbarState("Zurück", null, () -> this.mainFormController.hideUserSettings(), null);

    private StringProperty name = new SimpleStringProperty();
    private StringProperty email = new SimpleStringProperty();
    private StringProperty username = new SimpleStringProperty();
    private StringProperty credit = new SimpleStringProperty();
    private StringProperty authKey = new SimpleStringProperty();

    @Override
    public void onStart(MainFormController mfc) {
        this.mainFormController = mfc;
        this.userSettingsContent.setOnMouseClicked(event -> {
            // Klick auf Inhalt der Benutzereinstellungen soll diese nicht schließen
            event.consume();
        });
        this.userSettingsPane.setOnMouseClicked(e -> {
            // Bei Klick auf Hintergrund, schließe Pop-Up
            if (e.isConsumed()) return;
            this.mainFormController.hideUserSettings();
        });
    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onActivate() {
        this.mainFormController.registeredUserProperty().addListener(userChangedListener);
        this.name.set(this.mainFormController.getRegisteredUser().getName());
        this.username.set(this.mainFormController.getRegisteredUser().getUsername());
        this.email.set(this.mainFormController.getRegisteredUser().getEmail());
        if (this.email.get() == null || this.email.get().isEmpty()) {
            this.email.set("<leer>");
        }
        this.userSettingsPane.setVisible(true);
        this.credit.set(FormatUtilities.formatCurrency(this.mainFormController.getRegisteredUser().getCredit()));
        this.authKey.set(null);
        this.authKeyInfo.setVisible(false);
        this.appConnectionInfo.setVisible(true);
    }

    @Override
    public void onDeactivate() {
        this.mainFormController.registeredUserProperty().removeListener(userChangedListener);
        this.userSettingsPane.setVisible(false);
    }

    @Override
    public void onReturnFromError() {

    }

    public void onAuthKeyInfo(MouseEvent mouseEvent) {
        this.mainFormController.hideUserSettings();
    }

    @Override
    public ToolbarState getToolbarState() {
        return toolbarState;
    }

    public void onLogout(ActionEvent actionEvent) {
        this.mainFormController.setRegisteredUser(null);
    }

    public String getName() {
        return name.get();
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String email) {
        this.email.set(email);
    }

    public StringProperty emailProperty() {
        return email;
    }

    public String getUsername() {
        return username.get();
    }

    public void setUsername(String username) {
        this.username.set(username);
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public String getCredit() {
        return credit.get();
    }

    public void setCredit(String credit) {
        this.credit.set(credit);
    }

    public StringProperty creditProperty() {
        return credit;
    }

    public String getAuthKey() {
        return authKey.get();
    }

    public StringProperty authKeyProperty() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey.set(authKey);
    }
}
