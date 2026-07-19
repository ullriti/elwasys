package org.kabieror.elwasys.webportal.components;

import com.vaadin.data.Item;
import com.vaadin.data.Validator.InvalidValueException;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.data.util.ObjectProperty;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.*;
import org.kabieror.elwasys.common.*;
import org.kabieror.elwasys.webportal.WashportalManager;
import org.kabieror.elwasys.webportal.events.IDeviceUpdatedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * Dieses Fenster erlaubt das Bearbeiten und Erstellen von Programmen
 *
 * @author Oliver Kabierschke
 *
 */
public class DeviceWindow extends Window {

    /**
     *
     */
    private static final long serialVersionUID = 6281036476918365426L;
    private static final String CAPTION_PROPERTY = "caption";
    private static final String VALUE_PROPERTY = "value";
    /**
     * Der Modus des Formulars
     */
    private final Mode mode;
    /**
     * Listener, die nach Abschluss benachrichtigt werden
     */
    private final List<IDeviceUpdatedEventListener> listeners = new Vector<>();
    private final ObjectProperty<Float> autoEndPowerThreasholdProperty = new ObjectProperty<Float>(2f);
    private final ObjectProperty<Integer> autoEndWaitTimeProperty = new ObjectProperty<Integer>(100);
    Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Das Programm, das bearbeitet wird
     */
    private Device deviceToEdit;
    /**
     * Der Name des Programms
     */
    private TextField tfName;
    /**
     * Die Anzeigeposition des Geräts
     */
    private ComboBox cbPosition;
    private IndexedContainer positionsContainer;
    /**
     * Der Standort des Geräts
     */
    private ComboBox cbLocation;
    private IndexedContainer locationsContainer;
    /**
     * Der Name des Geräts im FHEM Server.
     */
    private TextField tfFhemName;
    /**
     * Der Name des Schalters für das Gerät im FHEM Server.
     */
    private TextField tfFhemSwitchName;
    /**
     * Der Name des Leistungs-Messungs-Kanals für das Gerät im FHEM Server.
     */
    private TextField tfFhemPowerName;
    /**
     * Die UUID des Zigbee-Gerätes im deCONZ-Gateway.
     */
    private TextField tfDeconzUuid;
    /**
     * Der Schwellwert für das automatische Beenden von auf diesem Gerät
     * laufenden Programmen.
     */
    private TextField tfAutoEndPowerThreashold;
    /**
     * Die Zeit, die nach dem Unterschreiben des Schwellwerts gewartet werden
     * soll, bevor das Programm automatisch beendet wird.
     */
    private TextField tfAutoEndWaitTime;
    /**
     * Der Aktivierungzustand des Programms
     */
    private CheckBox cbEnabled;
    /**
     * Programmauswahl
     */
    private TwinColSelect selPrograms;
    private IndexedContainer programsContainer;

    /**
     * Benutzergruppen-Auswahl
     */
    private TwinColSelect selGroups;
    private IndexedContainer groupsContainer;

    /**
     * Konstruktor
     */
    public DeviceWindow(Device deviceToEdit) {
        this.mode = Mode.EDIT_DEVICE;

        this.deviceToEdit = deviceToEdit;
        this.init();

        this.tfName.setValue(deviceToEdit.getName());
        this.cbPosition.setValue(deviceToEdit.getPosition());
        this.tfFhemName.setValue(deviceToEdit.getFhemName());
        this.tfFhemSwitchName.setValue(deviceToEdit.getFhemSwitchName());
        this.tfFhemPowerName.setValue(deviceToEdit.getFhemPowerName());
        this.tfDeconzUuid.setValue(deviceToEdit.getDeconzUuid());
        this.autoEndPowerThreasholdProperty.setValue(deviceToEdit.getAutoEndPowerThreashold());
        this.autoEndWaitTimeProperty
                .setValue(new Long(deviceToEdit.getAutoEndWaitTime().getSeconds()).intValue());
        this.cbEnabled.setValue(deviceToEdit.isEnabled());
        this.cbLocation.setValue(deviceToEdit.getLocation().getId());

        final HashSet<Integer> selectedPrograms = new HashSet<>();
        for (final Program p : deviceToEdit.getPrograms()) {
            selectedPrograms.add(p.getId());
        }
        this.selPrograms.setValue(selectedPrograms);

        HashSet<Integer> selectedGroups = new HashSet<>();
        for (UserGroup g : deviceToEdit.getValidUserGroups()) {
            selectedGroups.add(g.getId());
        }
        this.selGroups.setValue(selectedGroups);
    }

    public DeviceWindow() {
        this.mode = Mode.CREATE_DEVICE;
        this.init();
    }

    /**
     * Komponenten initialisieren
     */
    @SuppressWarnings("unchecked")
    private void init() {
        String caption;
        String btnSaveCaption;
        if (this.mode.equals(Mode.EDIT_DEVICE)) {
            caption = "Gerät bearbeiten";
            btnSaveCaption = "Speichern";
        } else {
            caption = "Gerät erstellen";
            btnSaveCaption = "Erstellen";
        }

        // Beschriftung des Fensters
        this.setCaption(caption);
        this.setWidth("50em");
        this.setResizable(false);
        this.setModal(true);

        final VerticalLayout content = new VerticalLayout();
        this.setContent(content);
        content.setMargin(true);
        content.setSpacing(true);

        final FormLayout form = new FormLayout();
        content.addComponent(form);
        form.setWidth("30em");
        form.setMargin(false);

        // Textfeld: Name
        this.tfName = new TextField("Name");
        form.addComponent(this.tfName);
        this.tfName.setRequired(true);
        this.tfName.setRequiredError("Bitte Name eingeben.");
        this.tfName.setValidationVisible(false);
        this.tfName.setWidth("100%");

        // Auswahl: Anzeigeposition
        this.cbPosition = new ComboBox("Position");
        form.addComponent(this.cbPosition);
        this.cbPosition.setRequired(true);
        this.cbPosition.setRequiredError("Bitte Position auswählen.");
        this.cbPosition.setValidationVisible(false);
        this.cbPosition.setDescription("Die Gerätenummer, in welcher das Gerät im elwaClient angezeigt werden soll.");
        this.positionsContainer = new IndexedContainer();
        this.positionsContainer.addContainerProperty(CAPTION_PROPERTY, Integer.class, 1);
        for (int i = 1; i <= 4; i++) {
            this.positionsContainer.addItem(i).getItemProperty(CAPTION_PROPERTY).setValue(i);
        }
        this.cbPosition.setItemCaptionPropertyId(CAPTION_PROPERTY);
        this.cbPosition.setContainerDataSource(this.positionsContainer);


        // Auswahl: Standort
        this.cbLocation = new ComboBox("Standort");
        form.addComponent(this.cbLocation);
        this.cbLocation.setRequired(true);
        this.cbLocation.setRequiredError("Bitte Standort auswählen");
        this.cbLocation.setValidationVisible(false);
        this.cbLocation.setNewItemsAllowed(true);
        this.cbLocation.setNewItemHandler(i -> this.addNewLocation(i));
        this.locationsContainer = new IndexedContainer();
        this.cbLocation.setContainerDataSource(this.locationsContainer);
        this.cbLocation.setItemCaptionPropertyId(CAPTION_PROPERTY);
        this.locationsContainer.addContainerProperty(CAPTION_PROPERTY, String.class, null);
        this.locationsContainer.addContainerProperty(VALUE_PROPERTY, Location.class, null);

        try {
            for (final Location l : WashportalManager.instance.getDataManager().getLocations()) {
                final Item i = this.locationsContainer.addItem(l.getId());
                i.getItemProperty(CAPTION_PROPERTY).setValue(l.getName());
                i.getItemProperty(VALUE_PROPERTY).setValue(l);
            }
        } catch (final SQLException e2) {
            this.logger.error("Could not load the available locations");
            WashportalManager.instance.showDatabaseError(e2);
        }

        // Textfeld: FHEM Name
        this.tfFhemName = new TextField("Fhem Name");
        form.addComponent(this.tfFhemName);
        this.tfFhemName.setDescription("Der Name des Gerätes im Fhem Server.");
        this.tfFhemName.setRequired(true);
        this.tfFhemName.setRequiredError("Bitte Namen angeben.");
        this.tfFhemName.setValidationVisible(false);
        this.tfFhemName.setWidth("100%");

        // Textfeld: FHEM Switch Name
        this.tfFhemSwitchName = new TextField("Fhem Switch Name");
        form.addComponent(this.tfFhemSwitchName);
        this.tfFhemSwitchName
                .setDescription("Der Name des Schalters für das Gerät im Fhem Server.");
        this.tfFhemSwitchName.setRequired(true);
        this.tfFhemSwitchName.setRequiredError("Bitte Namen angeben.");
        this.tfFhemSwitchName.setValidationVisible(false);
        this.tfFhemSwitchName.setWidth("100%");

        // Textfeld: FHEM Power Name
        this.tfFhemPowerName = new TextField("Fhem Power Name");
        form.addComponent(this.tfFhemPowerName);
        this.tfFhemPowerName.setDescription(
                "Der Name des Leistungs-Messungs-Kanals für das Gerät im Fhem Server.");
        this.tfFhemPowerName.setRequired(true);
        this.tfFhemPowerName.setRequiredError("Bitte Namen angeben.");
        this.tfFhemPowerName.setValidationVisible(false);
        this.tfFhemPowerName.setWidth("100%");

        // Textfeld: deCONZ UUID (Zigbee-Gateway; alternativ zu Fhem)
        this.tfDeconzUuid = new TextField("deCONZ UUID");
        form.addComponent(this.tfDeconzUuid);
        this.tfDeconzUuid.setDescription("Die UUID des Zigbee-Gerätes im deCONZ-Gateway.");
        this.tfDeconzUuid.setValidationVisible(false);
        this.tfDeconzUuid.setWidth("100%");

        // Textfeld: Auto-End Leistungs-Schwellwert
        this.tfAutoEndPowerThreashold = new TextField("Auto-Ende Schwellwert (W)");
        this.tfAutoEndPowerThreashold.setDescription(
                "Der Schwellwert für die abgenommene Leistung des Geräts in Watt, bei dessen Unterschreitung "
                        + "das laufende Programm beendet wird.");
        this.tfAutoEndPowerThreashold.setRequired(true);
        this.tfAutoEndPowerThreashold.setRequiredError("Bitte Schwellwert eingeben.");
        this.tfAutoEndPowerThreashold.setValidationVisible(false);
        this.tfAutoEndPowerThreashold.setWidth("10em");
        this.tfAutoEndPowerThreashold.setPropertyDataSource(this.autoEndPowerThreasholdProperty);
        form.addComponent(this.tfAutoEndPowerThreashold);

        this.tfAutoEndWaitTime = new TextField("Auto-Ende Wartezeit (s)");
        this.tfAutoEndWaitTime.setDescription(
                "Die Zeit in Sekunden, die nach Unterschreiten des Auto-Ende-Schwellwerts gewartet werden soll, "
                        + "bevor das laufende Programm beendet wird.");
        this.tfAutoEndWaitTime.setRequired(true);
        this.tfAutoEndWaitTime.setRequiredError("Bitte Wartezeit eingeben.");
        this.tfAutoEndWaitTime.setValidationVisible(false);
        this.tfAutoEndWaitTime.setWidth("10em");
        this.tfAutoEndWaitTime.setPropertyDataSource(this.autoEndWaitTimeProperty);
        form.addComponent(this.tfAutoEndWaitTime);

        // CheckBox: Aktiviert
        this.cbEnabled = new CheckBox("Aktiviert");
        form.addComponent(this.cbEnabled);

        // ==== Programme ====
        // Verfügbare Programme laden
        try {
            this.programsContainer = new IndexedContainer();
            this.programsContainer.addContainerProperty(CAPTION_PROPERTY, String.class, "");
            this.programsContainer.addContainerProperty(VALUE_PROPERTY, Program.class, "");
            for (final Program p : WashportalManager.instance.getDataManager().getPrograms()) {
                final Item i = this.programsContainer.addItem(p.getId());
                i.getItemProperty(CAPTION_PROPERTY).setValue(p.getName());
                i.getItemProperty(VALUE_PROPERTY).setValue(p);
            }
        } catch (final UnsupportedOperationException e2) {
            this.logger.error("Error while loading the available programs.", e2);
            WashportalManager.instance.showError("Internal Error",
                    "The available programs could not be loaded.");
        } catch (final SQLException e2) {
            this.logger.error("Could not load the available programs from the database.", e2);
            WashportalManager.instance.showDatabaseError(e2);
        }

        // Komponente erstellen
        final Label programsCaptionLabel = new Label(
                "<h3 style='margin-top:0;margin-bottom:0'>Programme</h3>", ContentMode.HTML);
        content.addComponent(programsCaptionLabel);
        this.selPrograms = new TwinColSelect();
        content.addComponent(this.selPrograms);
        this.selPrograms.setLeftColumnCaption("Verfügbar");
        this.selPrograms.setRightColumnCaption("Aktiv");
        this.selPrograms.setSizeFull();
        this.selPrograms.setNewItemsAllowed(false);
        this.selPrograms.setRows(5);
        this.selPrograms.setContainerDataSource(this.programsContainer);
        this.selPrograms.setItemCaptionPropertyId(CAPTION_PROPERTY);

        // ==== Benutzergruppen ====
        // Verfügbare Gruppen laden
        try {
            this.groupsContainer = new IndexedContainer();
            this.groupsContainer.addContainerProperty(CAPTION_PROPERTY, String.class, "");
            this.groupsContainer.addContainerProperty(VALUE_PROPERTY, UserGroup.class, "");
            for (final UserGroup g : WashportalManager.instance.getDataManager().getUserGroups()) {
                final Item i = this.groupsContainer.addItem(g.getId());
                i.getItemProperty(CAPTION_PROPERTY).setValue(g.getName());
                i.getItemProperty(VALUE_PROPERTY).setValue(g);
            }
        } catch (final UnsupportedOperationException e2) {
            this.logger.error("Error while loading the available user groups.", e2);
            WashportalManager.instance
                    .showError("Interner Fehler", "Die verfügbaren Benutzergruppen konnten nicht geladen werden.");
        } catch (final SQLException e2) {
            this.logger.error("Konnte die verfügbaren Benutzergruppen nicht aus der Datenbank laden.", e2);
            WashportalManager.instance.showDatabaseError(e2);
        }

        // Komponente erstellen
        final Label userGroupsCaptionLabel =
                new Label("<h3 style='margin-top:0;margin-bottom:0'>Benutzergruppen</h3>", ContentMode.HTML);
        content.addComponent(userGroupsCaptionLabel);
        this.selGroups = new TwinColSelect();
        content.addComponent(this.selGroups);
        this.selGroups.setLeftColumnCaption("Gesperrt");
        this.selGroups.setRightColumnCaption("Freigegeben");
        this.selGroups.setSizeFull();
        this.selGroups.setNewItemsAllowed(false);
        this.selGroups.setRows(5);
        this.selGroups.setContainerDataSource(this.groupsContainer);
        this.selGroups.setItemCaptionPropertyId(CAPTION_PROPERTY);

        // Formularfuß mit Buttons
        final HorizontalLayout footer = new HorizontalLayout();
        content.addComponent(footer);
        footer.setWidth("100%");
        footer.setSpacing(true);
        footer.addStyleName("v-window-bottom-toolbar");

        final Label footerText = new Label("");
        footer.addComponent(footerText);
        footer.setExpandRatio(footerText, 1);

        final Button btnCancel = new Button("Abbrechen");
        footer.addComponent(btnCancel);
        btnCancel.addClickListener(e -> this.exitWindow());

        final Button btnSave = new Button(btnSaveCaption);
        footer.addComponent(btnSave);
        btnSave.addClickListener(e -> {
            try {
                this.save();
            } catch (final SQLException e1) {
                this.logger.error("Unable to store device into database.", e1);
                WashportalManager.instance.showDatabaseError(e1);
            } catch (final Exception e1) {
                this.logger.error("Unable to save device.", e1);
                WashportalManager.instance.showError(e1);
            }
        });
        btnSave.addStyleName("primary");
        btnSave.setClickShortcut(KeyCode.ENTER);
    }

    /**
     * Löscht nicht verwendete Standorte und schließt das Fenster
     */
    private void exitWindow() {
        try {
            WashportalManager.instance.getDataManager().removeUnusedLocations();
        } catch (final SQLException e) {
            this.logger.error("Could not remove unused locations.", e);
            WashportalManager.instance.showDatabaseError(e);
        } catch (final Exception e1) {
            this.logger.error("Could not remove unused locations.", e1);
            WashportalManager.instance.showError(e1);
        }
        this.setVisible(false);
        this.getUI().removeWindow(this);
    }

    /**
     * Speichert das Formular in die Datenbank
     *
     * @throws SQLException
     * @throws NoDataFoundException
     */
    @SuppressWarnings("unchecked")
    private void save() throws SQLException {
        // Felder validieren
        try {
            this.tfName.validate();
            this.cbPosition.validate();
            this.tfFhemName.validate();
            this.tfFhemSwitchName.validate();
            this.tfFhemPowerName.validate();
            this.tfAutoEndPowerThreashold.validate();
            this.tfAutoEndWaitTime.validate();
            this.cbLocation.validate();
        } catch (final InvalidValueException e) {
            this.tfName.setValidationVisible(true);
            this.cbPosition.setValidationVisible(true);
            this.tfFhemName.setValidationVisible(true);
            this.tfFhemSwitchName.setValidationVisible(true);
            this.tfFhemPowerName.setValidationVisible(true);
            this.tfAutoEndPowerThreashold.setValidationVisible(true);
            this.tfAutoEndWaitTime.setValidationVisible(true);
            this.cbLocation.setValidationVisible(true);
            return;
        }

        // Load location
        final Location location = (Location) this.locationsContainer
                .getContainerProperty(this.cbLocation.getValue(), VALUE_PROPERTY).getValue();

        final int position = (Integer) this.cbPosition.getValue();

        // Load selected programs
        final List<Program> programs = new Vector<>();
        for (final Integer i : (Set<Integer>) (this.selPrograms.getValue())) {
            programs.add((Program) this.programsContainer.getItem(i).getItemProperty(VALUE_PROPERTY)
                    .getValue());
        }

        // Load selected user groups
        final List<UserGroup> validUserGroups = new Vector<>();
        for (final Integer i : (Set<Integer>) (this.selGroups.getValue())) {
            validUserGroups.add((UserGroup) this.groupsContainer.getItem(i).getItemProperty(VALUE_PROPERTY).getValue());
        }

        Device device;
        switch (this.mode) {
        case CREATE_DEVICE:
            device = new Device(WashportalManager.instance.getDataManager(), this.tfName.getValue(),
                    position, location, this.tfFhemName.getValue(),
                    this.tfFhemSwitchName.getValue(), this.tfFhemPowerName.getValue(),
                    this.tfDeconzUuid.getValue(),
                    this.autoEndPowerThreasholdProperty.getValue(),
                    Duration.ofSeconds(this.autoEndWaitTimeProperty.getValue()), this.cbEnabled.getValue(), programs,
                    validUserGroups);
            break;
        case EDIT_DEVICE:
            device = this.deviceToEdit;
            device.modify(this.tfName.getValue(), position, location, this.tfFhemName.getValue(),
                    this.tfFhemSwitchName.getValue(), this.tfFhemPowerName.getValue(),
                    this.tfDeconzUuid.getValue(),
                    this.autoEndPowerThreasholdProperty.getValue(),
                    Duration.ofSeconds(this.autoEndWaitTimeProperty.getValue()), this.cbEnabled.getValue(), programs,
                    validUserGroups);
            break;
        default:
            this.logger.error("Unknown state. Cannot save user.");
            WashportalManager.instance.showError("Zustandsfehler",
                    "Dieses Fenster hat einen ungültigen Zustand.");
            return;
        }

        // Listener benachrichtigen
        for (final IDeviceUpdatedEventListener l : this.listeners) {
            l.onDeviceUpdated(device);
        }

        this.exitWindow();
    }

    @SuppressWarnings("unchecked")
    private void addNewLocation(String name) {
        // Prüfe, ob es schon einen Standort mit diesem Namen gibt
        try {
            final Location l = new Location(WashportalManager.instance.getDataManager(), name);
            final Item item = this.locationsContainer.addItem(l.getId());
            item.getItemProperty(CAPTION_PROPERTY).setValue(name);
            item.getItemProperty(VALUE_PROPERTY).setValue(l);
            this.cbLocation.select(l.getId());
        } catch (final SQLException e1) {
            this.logger.error("Could not create new location");
            WashportalManager.instance.showDatabaseError(e1);
        } catch (final Exception e1) {
            this.logger.error("Could not create new location.", e1);
            WashportalManager.instance.showError(e1);
        }
    }

    /**
     * Fügt einen Listener hinzu, der benachrichtigt werden möchte, sobald ein
     * Gerät erstellt oder bearbeitet wurde.
     *
     * @param l
     *            Der Listener
     */
    public void addDeviceUpdatedEventListener(IDeviceUpdatedEventListener l) {
        this.listeners.add(l);
    }

    /**
     * Der Modus, in dem das Fenster geöffnet werden kann
     *
     * @author Oliver Kabierschke
     */
    public enum Mode {
        EDIT_DEVICE, CREATE_DEVICE,
    }
}
