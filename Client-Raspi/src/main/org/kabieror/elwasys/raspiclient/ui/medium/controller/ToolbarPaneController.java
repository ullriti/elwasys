package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.kabieror.elwasys.raspiclient.ui.UiUtilities;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller für die Toolbar
 */
public class ToolbarPaneController implements Initializable {
    private static final String NOT_LOGGED_IN_STRING = "Nicht angemeldet";

    @FXML
    private Button backButton;
    @FXML
    private Label backButtonText;
    @FXML
    private Button forwardButton;
    @FXML
    private Label forwardButtonText;
    @FXML
    private Button userButton;
    @FXML
    private Label userButtonText;
    @FXML
    private HBox cardUnknownNotice;
    @FXML
    private HBox userBlockedNotice;
    @FXML
    private HBox locationDisallowedNotice;
    @FXML
    private HBox userInfo;
    @FXML
    private HBox offlineNotice;

    private StringProperty userName = new SimpleStringProperty(NOT_LOGGED_IN_STRING);

    /**
     * Das geplante Zurücksetzen der Visualisierung einer unbekannten Karte
     */
    private ScheduledFuture resetUnknownCardFuture;
    private MainFormController mainFormController;

    /**
     * Initialisiert die Komponenten nachdem die Oberfläche dieser Komponente geladen ist
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    /**
     * Wird aufgerufen, sobald alle Manager geladen sind und die Benutzeroberfläche mit Daten befüllt werden soll.
     *
     * @param mainFormController Der Haupt-Controller.
     */
    public void onStart(MainFormController mainFormController) {

        this.mainFormController = mainFormController;
        this.mainFormController.registeredUserProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                Platform.runLater(() -> this.userName.set(NOT_LOGGED_IN_STRING));
            } else {
                Platform.runLater(() -> this.userName.set(newValue.getName()));
            }
            if (this.resetUnknownCardFuture != null && !this.resetUnknownCardFuture.isDone()) {
                this.resetUnknownCardFuture.cancel(false);
                this.resetUnknownCardStatus();
            }
        });
    }

    /**
     * Setzt den Status einer unbekannten Karte zurück.
     */
    private void resetUnknownCardStatus() {
        UiUtilities.setStyleClass(this.userInfo, "card-unknown", false);
        UiUtilities.setStyleClass(this.userInfo, "user-blocked", false);
        UiUtilities.setStyleClass(this.userInfo, "location-disallowed", false);
        this.resetUnknownCardFuture = null;
    }

    /**
     * Wird aufgerufen, sobald die Anwendung dabei ist, heruntergefahren zu werden.
     */
    public void onTerminate() {

    }

    public void setToolbarState(ToolbarState tbs) {
        boolean off = false;
        if (tbs == null) {
            off = true;
        }
        this.forwardButton.setVisible(!off && tbs.hasForwardOption());
        this.forwardButton.setDisable(off || tbs.isForwardOptionDisabled());
        this.forwardButtonText.setText(off ? "" : tbs.getForwardText());
        this.forwardButton.setOnAction(off ? null : e -> {
            if (tbs.getForwardAction() != null) {
                tbs.getForwardAction().run();
            }
        });

        this.backButton.setVisible(!off && tbs.hasBackOption());
        this.backButton.setDisable(off || tbs.isBackOptionDisabled());
        this.backButtonText.setText(off ? "" : tbs.getBackText());
        this.backButton.setOnAction(off ? null : e -> {
            if (tbs.getBackAction() != null) {
                tbs.getBackAction().run();
            }
        });

        this.userButton.setVisible(!off);
        this.userButton.setDisable(off || tbs.isUserButtonDisabled());
    }

    /**
     * Blendet den Offline-Hinweis ein oder aus.
     * <p>
     * Befund aus dem bewussten Offline-Test am Terminal Hilarenhaus (2026-09-20): Das Terminal
     * arbeitete bei getrenntem Backend fachlich einwandfrei weiter (lokales Journal, spaeterer
     * Nachtrag ohne Datenverlust) - der Bewohner konnte davon aber NICHTS sehen. Im gesamten
     * Oberflaechen-Paket kam "offline" bis dahin nur in Kommentaren und in der Ausweichlogik
     * vor: kein Hinweistext, kein Banner, kein Statuspunkt. Zusammen mit der damaligen Totzeit
     * von zweimal 10 s (siehe {@code api.ApiClient}) war die wahrscheinlichste Reaktion, die
     * Karte erneut aufzulegen - genau das, was bei einem gestoerten System am wenigsten hilft.
     */
    public void setBackendOffline(boolean offline) {
        this.offlineNotice.setVisible(offline);
    }

    /**
     * Visualisiert dem Benutzer, dass die eingescannte Karte unbekannt ist.
     */
    public void visualizeUnknownId() {
        if (this.resetUnknownCardFuture != null && !this.resetUnknownCardFuture.isDone()) {
            this.resetUnknownCardFuture.cancel(false);
        }
        this.resetUnknownCardStatus();
        UiUtilities.setStyleClass(this.userInfo, "card-unknown", true);
        this.resetUnknownCardFuture = this.mainFormController.getUpdateService()
                .schedule(() -> Platform.runLater(this::resetUnknownCardStatus), 5, TimeUnit.SECONDS);
    }

    /**
     * Visualisiert dem Benutzer, dass die eingescannte Karte unbekannt ist.
     */
    public void visualizeBlockedUser() {
        if (this.resetUnknownCardFuture != null && !this.resetUnknownCardFuture.isDone()) {
            this.resetUnknownCardFuture.cancel(false);
        }
        this.resetUnknownCardStatus();
        UiUtilities.setStyleClass(this.userInfo, "user-blocked", true);
        this.resetUnknownCardFuture = this.mainFormController.getUpdateService()
                .schedule(() -> Platform.runLater(this::resetUnknownCardStatus), 5, TimeUnit.SECONDS);
    }

    /**
     * Visualisiert dem Benutzer, dass die eingescannte Karte unbekannt ist.
     */
    public void visualizeLocationNotAllowed() {
        if (this.resetUnknownCardFuture != null && !this.resetUnknownCardFuture.isDone()) {
            this.resetUnknownCardFuture.cancel(false);
        }
        this.resetUnknownCardStatus();
        UiUtilities.setStyleClass(this.userInfo, "location-disallowed", true);
        this.resetUnknownCardFuture = this.mainFormController.getUpdateService()
                .schedule(() -> Platform.runLater(this::resetUnknownCardStatus), 5, TimeUnit.SECONDS);
    }


    public String getUserName() {
        return userName.get();
    }

    public void setUserName(String userName) {
        this.userName.set(userName);
    }

    public StringProperty userNameProperty() {
        return userName;
    }

    public void onUserClick(ActionEvent actionEvent) {
        if (this.mainFormController != null) {
            this.mainFormController.showUserSettings();
        }
    }
}
