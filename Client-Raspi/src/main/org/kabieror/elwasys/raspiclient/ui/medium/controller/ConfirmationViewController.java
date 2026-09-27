package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.kabieror.elwasys.common.FormatUtilities;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceDto;
import org.kabieror.elwasys.raspiclient.application.ActionContainer;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.executions.FhemException;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.model.ClientUser;
import org.kabieror.elwasys.raspiclient.ui.ComponentControlInstance;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.UiUtilities;
import org.kabieror.elwasys.raspiclient.ui.medium.IViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controller der Bestätigungsseite für den Programmstart.
 * <p>
 * Seit Phase 4 AP4 (siehe docs/kb/05-migration-plan.md "Benachrichtigungen") steuern die
 * E-Mail-/Push-Checkboxen nichts mehr fachlich an: der Alt-Code speicherte die
 * Einwilligung ohnehin nur im (nie persistierten) In-Memory-Benutzerobjekt für den
 * unmittelbar folgenden Versand durch {@code ExecutionFinisher} - dieser Versand läuft
 * jetzt zentral im Backend anhand der dort gespeicherten Benutzer-Einstellung. Die
 * Checkboxen/Hinweise bleiben (FXML unangetastet) sichtbar, wirken aber nicht mehr auf den
 * Versand - eine bewusste, explizit beauftragte Verhaltensänderung (Notification-Entfernung),
 * kein übersehener Rest. Die Auth-Key-Anzeige der elwaApp-Kopplung (Alt-Spalte
 * {@code users.auth_key}, eine bewusst nicht ins neue Datenmodell gemappte App-Altlast,
 * siehe {@code backend.domain.UserEntity}) wurde in Phase 5 AP4 (V10) entfernt.
 */
public class ConfirmationViewController implements Initializable, IViewController {

    /**
     * Spaetestes Programmende. An die Anzeige-Locale gebunden statt an die JVM-Standard-Locale
     * (Issue #100) - der Betrag direkt daneben ist es laengst, beides gehoert in dieselbe
     * Schreibweise. {@link DateTimeFormatter} ist threadsicher, deshalb hier statisch.
     */
    private static final DateTimeFormatter LATEST_END_FORMATTER =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
                    .withLocale(FormatUtilities.DISPLAY_LOCALE);

    private Logger logger = LoggerFactory.getLogger(ConfirmationViewController.class);

    private MainFormController mfc;

    private ClientProgram selectedProgram;

    private ToolbarState toolbarStateWait =
            new ToolbarState("Zurück", "Start", () -> this.mfc.gotoState(MainFormState.SELECT_DEVICE), null, false,
                    true);
    /**
     * Alle Beschriftungen dieser Seite starten mit dem Platzhalter statt mit {@code null}:
     * eine leere Beschriftung bringt den JavaFX-Layout-Durchlauf zum Absturz und friert die
     * Seite dauerhaft ein (ausführlich in {@link UiUtilities#setLabelText}). Die Seite wird
     * bereits vor ihrer ersten Benutzung gelayoutet - unsichtbar, aber im Layout.
     */
    private StringProperty titleText = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty maxPrice = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty userCredit = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty remainingCredit = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty latestEnd = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    /**
     * Die Texte der beiden Benachrichtigungs-Checkboxen starten bewusst NICHT leer, sondern
     * mit dem Platzhalter: eine leere Beschriftung an einem gelayouteten Bedienelement bringt
     * den JavaFX-Layout-Durchlauf zum Absturz und friert damit diese Seite dauerhaft ein
     * (ausführlich in {@link UiUtilities#setLabelText}). Der Push-Text wird seit dem Entfernen
     * der elwaApp-Kopplung überhaupt nicht mehr gesetzt, war also bis zum ersten
     * {@link #selectProgram} sogar {@code null}.
     */
    private StringProperty emailNotificationText = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty ionicNotificationText = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty registeredUserUserName = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty moreInfoText = new SimpleStringProperty(UiUtilities.BLANK_LABEL_TEXT);
    private StringProperty portalUrl = new SimpleStringProperty();

    /**
     * Liste aller Programme zum ausgewählten Gerät
     */
    private Map<ClientProgram, ComponentControlInstance<ProgramListEntry>> programs = new HashMap<>();
    @FXML
    private VBox programsContainer;
    @FXML
    private Pane confirmationPane;
    @FXML
    private GridPane creditCalculation;
    @FXML
    private Label autoEndNotice;
    @FXML
    private CheckBox emailNotificationCheckBox;
    @FXML
    private Label emailNotificationErrorNote;
    @FXML
    private CheckBox ionicNotificationCheckBox;
    @FXML
    private Node ionicNotificationErrorNote;

    private ToolbarState toolbarStateReady =
            new ToolbarState("Zurück", "Start", () -> this.mfc.gotoState(MainFormState.SELECT_DEVICE),
                    this::onStartProgram);
    /**
     * Reagiert auf die Änderung des angemeldeten Benutzers.
     */
    private ChangeListener<? super ClientUser> registeredUserChangedListener = (observable, oldValue, newValue) -> {
        this.mfc.gotoState(MainFormState.SELECT_DEVICE);
    };

    /**
     * Initialisiert die Komponenten nachdem die Oberfläche dieser Komponente geladen ist
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    /**
     * Wird aufgerufen, sobald alle Manager geladen sind und die Benutzeroberfläche mit Daten befüllt werden soll.
     *
     * @param mfc Der Haupt-Controller.
     */
    public void onStart(MainFormController mfc) {
        this.mfc = mfc;
    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onActivate() {
        if (this.mfc == null) {
            this.logger.error("Main form controller is not set.");
            return;
        }
        if (this.mfc.getRegisteredUser() == null) {
            this.logger.error("No user is registered. Cannot proceed to confirmation page.");
            this.mfc.displayError("Nicht angemeldet",
                    "Bitte Karte auflegen und erst dann ein Gerät zur Buchung wählen.", null, true);
            return;
        }

        this.confirmationPane.setVisible(true);
        this.mfc.registeredUserProperty().addListener(this.registeredUserChangedListener);

        // Vor dem Laden der Programme: die Seite ist ab hier sichtbar, das Laden unten ist ein
        // Netzwerkaufruf. Alles, was ohne ihn schon feststeht, gehört deshalb JETZT auf den
        // Bildschirm - sonst steht in diesem Fenster gar nichts (nach dem Zurücksetzen in
        // resetUserBoundFields) oder, wie vor dem Fix, der vorige Benutzer.
        UiUtilities.setLabelText(this.registeredUserUserName, this.mfc.getRegisteredUser().getUsername());
        UiUtilities.setLabelText(this.userCredit,
                FormatUtilities.formatCurrency(this.mfc.getRegisteredUser().getCredit()));

        this.setPortalUrl(ElwaManager.instance.getConfigurationManager().getPortalUrl());
        UiUtilities.setLabelText(this.moreInfoText,
                "Mehr Informationen in der elwaApp und unter " + this.getPortalUrl());

        // Lade Programme (bereits gruppengefiltert und mit dem Preis für diesen
        // Benutzer bepreist, siehe DeviceDto#programs()).
        List<ClientProgram> progs;
        try {
            progs = this.loadProgramsForSelectedDevice();
        } catch (ApiException e) {
            this.logger.error("Could not load programs for the selected device.", e);
            this.mfc.displayError("Kommunikationsfehler", e.getLocalizedMessage(),
                    new org.kabieror.elwasys.raspiclient.application.ActionContainer(this::onActivate), true);
            return;
        }
        for (ClientProgram p : progs) {
            ComponentControlInstance<ProgramListEntry> i = ProgramListEntry.createInstance();
            i.getController().setProgram(p);
            i.getController().setController(this);
            i.getController().onStart(this.mfc);
            this.programsContainer.getChildren().add(i.getComponent());
            this.programs.put(p, i);
        }

        // Wähle automatisch erstes Programm vor.
        if (progs.size() > 0) {
            this.selectProgram(progs.get(0));
        }
    }

    /**
     * Lädt die für das gewählte Gerät verfügbaren Programme des angemeldeten Benutzers
     * (gruppengefiltert, mit Preis) - entspricht {@code Common.Device#getPrograms(User)}.
     */
    private List<ClientProgram> loadProgramsForSelectedDevice() throws ApiException {
        int userId = this.mfc.getRegisteredUser().getId();
        int deviceId = this.mfc.getSelectedDevice().getId();
        // Faellt bei einem Kommunikationsfehler auf den lokalen Offline-Snapshot zurueck
        // (Phase 4 AP6, siehe docs/kb/05-migration-plan.md).
        List<DeviceDto> devices = ElwaManager.instance.getDevicesForUser(userId);
        for (DeviceDto d : devices) {
            if (d.id() == deviceId) {
                return d.programs().stream().map(ClientProgram::of).toList();
            }
        }
        return List.of();
    }

    @Override
    public void onDeactivate() {
        this.confirmationPane.setVisible(false);
        this.mfc.registeredUserProperty().removeListener(this.registeredUserChangedListener);

        // Ansicht zurücksetzen
        this.emailNotificationCheckBox.setSelected(false);
        UiUtilities.setStyleClass(this.confirmationPane, "program-selected", false);
        this.selectedProgram = null;
        this.programs.clear();
        this.programsContainer.getChildren().clear();
        this.resetUserBoundFields();
    }

    /**
     * Leert alle Anzeigewerte, die zum gerade abgemeldeten Benutzer gehören.
     * <p>
     * Zwei Gründe, beide aus dem Vorfall vom 2026-09-24 (Terminal Hilarenhaus):
     * <ol>
     * <li>Diese Seite wird in {@link #onActivate} sichtbar geschaltet, BEVOR die Programme des
     * Benutzers geladen sind - dazwischen liegt ein Netzwerkaufruf. Bis {@link #selectProgram}
     * die neuen Werte schreibt, stand hier bisher noch Name, Guthaben und Preis des VORIGEN
     * Benutzers auf dem Bildschirm. Ein leeres Feld ist in diesem Moment die einzig ehrliche
     * Anzeige; eine fremde Identität mit fremdem Guthaben darf dort nie stehen.</li>
     * <li>Bleibt die Anzeige aus einem anderen Grund stehen (der eingefrorene Layout-Teilbaum
     * aus {@link UiUtilities#setLabelText}), zeigt sie danach leere Felder statt der Daten
     * eines fremden Benutzers.</li>
     * </ol>
     * Über {@link UiUtilities#setLabelText}, damit dabei keine leere Beschriftung entsteht.
     */
    private void resetUserBoundFields() {
        UiUtilities.setLabelText(this.titleText, null);
        UiUtilities.setLabelText(this.latestEnd, null);
        UiUtilities.setLabelText(this.userCredit, null);
        UiUtilities.setLabelText(this.maxPrice, null);
        UiUtilities.setLabelText(this.remainingCredit, null);
        UiUtilities.setLabelText(this.emailNotificationText, null);
        UiUtilities.setLabelText(this.registeredUserUserName, null);
    }

    @Override
    public void onReturnFromError() {

    }

    @Override
    public ToolbarState getToolbarState() {
        return isReady() ? this.toolbarStateReady : this.toolbarStateWait;
    }

    /**
     * Wird beim Klick des Benutzers auf die Schaltfläche "Start" aufgerufen und startet eine Programmausführung.
     */
    private void onStartProgram() {
        // Starte Programmausführung
        final ActionContainer actionContainer = new ActionContainer();
        actionContainer.setAction(() -> {
            final Thread actionThread = new Thread(() -> {
                assert this.mfc.getRegisteredUser() != null;
                assert this.mfc.getSelectedDevice() != null;
                assert this.selectedProgram != null;

                final ClientExecution ex;
                try {
                    // Faellt bei einem Kommunikationsfehler auf eine Offline-Buchung gegen den
                    // Snapshot zurueck (Phase 4 AP6, siehe docs/kb/05-migration-plan.md
                    // "Konzeptskizze: Offline-Buchungen am Terminal").
                    ex = ElwaManager.instance.createExecution(this.mfc.getRegisteredUser(),
                            this.mfc.getSelectedDevice(), this.selectedProgram);
                } catch (final ApiException e1) {
                    this.logger.error("The execution cannot be created.", e1);
                    Platform.runLater(() -> {
                        this.mfc.displayError("Kommunikationsfehler", e1.getLocalizedMessage(), actionContainer, true);
                        this.mfc.endWait();
                    });
                    return;
                }

                try {
                    ElwaManager.instance.getExecutionManager().startExecution(ex);
                    this.mfc.gotoState(MainFormState.SELECT_DEVICE);
                } catch (final IOException e1) {
                    this.logger.error("The execution could not be started.", e1);
                    this.mfc.displayError("Kommunikationsfehler", e1.getLocalizedMessage(), actionContainer, true);
                } catch (final InterruptedException e1) {
                    this.logger.error("The execution could not be started.", e1);
                    this.mfc.displayError("Interner Fehler", "Das Starten der Ausführung wurde unterbrochen.",
                            actionContainer, true);
                } catch (FhemException e) {
                    this.logger.error("Communication with FHEM-Server failed.", e);
                    this.mfc.displayError("Kommunikationsfehler",
                            e.getLocalizedMessage() + "\n" + e.getCause().getLocalizedMessage(), actionContainer, true);
                } catch (Exception e) {
                    this.logger.error("Could not start program", e);
                    this.mfc.displayError("Interner Fehler", e.getLocalizedMessage(), actionContainer, true);
                } finally {
                    this.mfc.endWait();
                }
            });
            this.mfc.beginWait();
            actionThread.start();
        });
        actionContainer.getAction().run();
    }

    /**
     * Wählt ein Programm aus und aktualisiert die Benutzeroberfläche entsprechend.
     *
     * @param program Das auszuwählende Programm.
     */
    void selectProgram(ClientProgram program) {
        if (program == null) {
            return;
        }
        if (this.selectedProgram != null) {
            this.programs.get(this.selectedProgram).getController().unselect();
        }
        this.selectedProgram = program;
        this.programs.get(program).getController().select();

        // Update confirmation panel
        UiUtilities.setStyleClass(this.confirmationPane, "program-selected", true);
        UiUtilities.setStyleClass(this.confirmationPane, "auto-end", this.selectedProgram.isAutoEnd());

        // Titeltext aktualisieren
        UiUtilities.setLabelText(this.titleText,
                this.selectedProgram.getName() + " auf " + this.mfc.getSelectedDevice().getName());

        // Guthabenberechnung aktualisieren (Preis kommt bereits fertig berechnet vom
        // Backend, siehe ClientProgram#getPriceAtMaxDuration()).
        UiUtilities.setLabelText(this.userCredit,
                FormatUtilities.formatCurrency(this.mfc.getRegisteredUser().getCredit()));

        BigDecimal maxPrice = this.selectedProgram.getPriceAtMaxDuration();
        UiUtilities.setLabelText(this.maxPrice, FormatUtilities.formatCurrency(maxPrice));

        UiUtilities.setLabelText(this.remainingCredit,
                FormatUtilities.formatCurrency(this.mfc.getRegisteredUser().getCredit().subtract(maxPrice)));

        UiUtilities.setStyleClass(this.confirmationPane, "credit-insufficient",
                !this.mfc.getRegisteredUser().canAfford(maxPrice));

        UiUtilities.setLabelText(this.latestEnd, LATEST_END_FORMATTER
                .format(LocalDateTime.now().plus(this.selectedProgram.getMaxDuration())));

        if (this.mfc.getRegisteredUser().getEmail() == null || this.mfc.getRegisteredUser().getEmail().isEmpty()) {
            UiUtilities.setStyleClass(this.confirmationPane, "email-not-set", true);
            // Auch die ausgeblendete Checkbox bekommt einen eigenen Text: sonst bliebe die
            // Adresse des vorigen Benutzers hier stehen (unsichtbar, aber sie wäre wieder da,
            // sobald der nächste Benutzer mit Adresse die Checkbox einblendet).
            UiUtilities.setLabelText(this.emailNotificationText, null);
        } else {
            UiUtilities.setStyleClass(this.confirmationPane, "email-not-set", false);
            UiUtilities.setLabelText(this.emailNotificationText,
                    "Bei Fertigstellung Email an " + this.mfc.getRegisteredUser().getEmail());
        }

        // elwaApp-Push ist entfernt (siehe Klassenkommentar) - immer "nicht verbunden".
        UiUtilities.setStyleClass(this.confirmationPane, "app-not-connected", true);

        // Toolbar aktualisieren
        this.mfc.updateToolbar();
    }


    /**
     * Ermittelt, ob die Ansicht bereit zum Start einer Programmausführung ist.
     *
     * @return True, wenn eine Ausführung gestartet werden kann.
     */
    private boolean isReady() {
        if (selectedProgram == null || this.mfc.getRegisteredUser() == null) {
            return false;
        }

        return this.mfc.getRegisteredUser().canAfford(this.selectedProgram.getPriceAtMaxDuration());
    }

    /**
     * Property: titleText
     */
    public String getTitleText() {
        return titleText.get();
    }

    public void setTitleText(String titleText) {
        this.titleText.set(titleText);
    }

    public StringProperty titleTextProperty() {
        return titleText;
    }

    /**
     * Property: maxPrice
     */
    public String getMaxPrice() {
        return maxPrice.get();
    }

    public void setMaxPrice(String maxPrice) {
        this.maxPrice.set(maxPrice);
    }

    public StringProperty maxPriceProperty() {
        return maxPrice;
    }

    /**
     * Property: userCredit
     */
    public String getUserCredit() {
        return userCredit.get();
    }

    public void setUserCredit(String userCredit) {
        this.userCredit.set(userCredit);
    }

    public StringProperty userCreditProperty() {
        return userCredit;
    }

    /**
     * Property: remainingCredit
     */
    public String getRemainingCredit() {
        return remainingCredit.get();
    }

    public void setRemainingCredit(String remainingCredit) {
        this.remainingCredit.set(remainingCredit);
    }

    public StringProperty remainingCreditProperty() {
        return remainingCredit;
    }

    /**
     * Property: latestEnd
     */
    public String getLatestEnd() {
        return latestEnd.get();
    }

    public void setLatestEnd(String latestEnd) {
        this.latestEnd.set(latestEnd);
    }

    public StringProperty latestEndProperty() {
        return latestEnd;
    }

    /**
     * Property: emailNotificationText
     */
    public String getEmailNotificationText() {
        return emailNotificationText.get();
    }

    public void setEmailNotificationText(String emailNotificationText) {
        this.emailNotificationText.set(emailNotificationText);
    }

    public StringProperty emailNotificationTextProperty() {
        return emailNotificationText;
    }

    /**
     * Property: ionicNotificationText
     */
    public String getIonicNotificationText() {
        return ionicNotificationText.get();
    }

    public StringProperty ionicNotificationTextProperty() {
        return ionicNotificationText;
    }

    public void setIonicNotificationText(String ionicNotificationText) {
        this.ionicNotificationText.set(ionicNotificationText);
    }

    /**
     * Property: registeredUserUserName
     */
    public String getRegisteredUserUserName() {
        return registeredUserUserName.get();
    }

    public void setRegisteredUserUserName(String registeredUserUserName) {
        this.registeredUserUserName.set(registeredUserUserName);
    }

    public StringProperty registeredUserUserNameProperty() {
        return registeredUserUserName;
    }

    /**
     * Property: portalUrl
     */
    public String getPortalUrl() {
        return portalUrl.get();
    }

    public StringProperty portalUrlProperty() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        this.portalUrl.set(portalUrl);
    }

    /**
     * Property: moreInfoText
     */
    public String getMoreInfoText() {
        return moreInfoText.get();
    }

    public StringProperty moreInfoTextProperty() {
        return moreInfoText;
    }

    public void setMoreInfoText(String moreInfoText) {
        this.moreInfoText.set(moreInfoText);
    }
}
