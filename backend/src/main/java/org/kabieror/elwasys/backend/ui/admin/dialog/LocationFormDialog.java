package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.UserGroupService;

/**
 * Modaler Dialog zum Anlegen/Bearbeiten eines Standorts - fachlicher Nachfolger von
 * {@code Portal/.../components/LocationWindow} (Alt-Portal, Testfall P14: Name vorbelegt,
 * unveränderter Save-Round-Trip). Feld "Benutzergruppen" entspricht 1:1 dem
 * {@code TwinColSelect} des Alt-Fensters (freigegebene Gruppen für diesen Standort). Anlegen
 * ist NEU (das Alt-Fenster kannte nur "Bearbeiten", siehe {@code AdminLayout}-Javadoc) - die
 * eigenständige Standort-Ansicht ist eine vom Auftraggeber gewünschte UX-Verbesserung.
 *
 * <p>Feld "Offline-Maximaldauer" (Phase 4 AP6, additiv - siehe kb/05-migration-plan.md
 * "Festlegungen zu den Offline-Detailfragen"): Auftraggeber-Auflage, dass
 * {@code offline.max-duration} über das Portal konfigurierbar sein muss. In Minuten, Default
 * {@link LocationService#DEFAULT_OFFLINE_MAX_DURATION_MINUTES}, wird an das Terminal über
 * {@code SnapshotDto#offlineMaxDurationMinutes()} ausgeliefert.
 */
public class LocationFormDialog extends Dialog {

    private final LocationService locationService;
    private final LocationEntity locationToEdit;

    private final TextField tfName = new TextField("Name");
    private final MultiSelectComboBox<UserGroupEntity> selGroups = new MultiSelectComboBox<>("Benutzergruppen");
    private final IntegerField ifOfflineMaxDuration = new IntegerField("Offline-Maximaldauer (Minuten)");

    public LocationFormDialog(LocationService locationService, UserGroupService userGroupService,
            LocationEntity locationToEdit, Runnable onSaved) {
        this.locationService = locationService;
        this.locationToEdit = locationToEdit;

        boolean editMode = locationToEdit != null;
        setHeaderTitle(editMode ? "Standort bearbeiten" : "Standort erstellen");
        setModal(true);
        setWidth("40em");

        this.tfName.setRequired(true);
        this.tfName.setWidthFull();

        this.selGroups.setItems(userGroupService.findAll());
        this.selGroups.setItemLabelGenerator(UserGroupEntity::getName);
        this.selGroups.setWidthFull();

        this.ifOfflineMaxDuration.setMin(1);
        this.ifOfflineMaxDuration.setStepButtonsVisible(true);
        this.ifOfflineMaxDuration.setWidthFull();
        this.ifOfflineMaxDuration.setHelperText(
                "Wie lange darf ein Terminal dieses Standorts ohne Backend-Verbindung eigenständig neue "
                        + "Buchungen annehmen, bevor es sie ablehnt?");
        this.ifOfflineMaxDuration.setValue(LocationService.DEFAULT_OFFLINE_MAX_DURATION_MINUTES);

        FormLayout form = new FormLayout(this.tfName, this.selGroups, this.ifOfflineMaxDuration);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        add(form);

        if (editMode) {
            this.tfName.setValue(locationToEdit.getName());
            this.selGroups.setValue(Set.copyOf(locationToEdit.getValidUserGroups()));
            this.ifOfflineMaxDuration.setValue(locationToEdit.getOfflineMaxDurationMinutes() != null
                    ? locationToEdit.getOfflineMaxDurationMinutes()
                    : LocationService.DEFAULT_OFFLINE_MAX_DURATION_MINUTES);
        }

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button(editMode ? "Speichern" : "Erstellen", e -> save(onSaved));
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSave));
    }

    private void save(Runnable onSaved) {
        if (this.tfName.isEmpty()) {
            this.tfName.setInvalid(true);
            this.tfName.setErrorMessage("Bitte Name eingeben.");
            return;
        }
        this.tfName.setInvalid(false);

        if (this.ifOfflineMaxDuration.isEmpty() || this.ifOfflineMaxDuration.getValue() < 1) {
            this.ifOfflineMaxDuration.setInvalid(true);
            this.ifOfflineMaxDuration.setErrorMessage("Bitte einen Wert von mindestens 1 Minute eingeben.");
            return;
        }
        this.ifOfflineMaxDuration.setInvalid(false);

        try {
            if (this.locationToEdit == null) {
                this.locationService.create(this.tfName.getValue(), this.selGroups.getValue(),
                        this.ifOfflineMaxDuration.getValue());
            } else {
                this.locationService.update(this.locationToEdit, this.tfName.getValue(), this.selGroups.getValue(),
                        this.ifOfflineMaxDuration.getValue());
            }
        } catch (RuntimeException e) {
            Notification notification = Notification.show(
                    "Der Standort konnte nicht gespeichert werden. " + e.getMessage(), 5000,
                    Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        close();
        onSaved.run();
    }
}
