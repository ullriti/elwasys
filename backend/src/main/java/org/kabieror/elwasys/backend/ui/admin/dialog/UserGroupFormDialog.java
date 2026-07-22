package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.util.Set;
import java.util.stream.Collectors;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;

/**
 * Modaler Dialog zum Anlegen/Bearbeiten einer Benutzergruppe - fachlicher Nachfolger von
 * {@code Portal/.../components/UserGroupWindow} (Alt-Portal, Testfall P9). Die drei
 * "TwinColSelect"-Auswahlen des Alt-Fensters (Standorte/Geräte/Programme, die für diese
 * Gruppe freigegeben sind) sind hier als {@link MultiSelectComboBox} umgesetzt - gleiche
 * Funktion (Mehrfachauswahl), moderneres Bedienelement (siehe docs/kb/05-migration-plan.md,
 * "Entscheidungen", Gestaltungsrahmen Portal-Neubau: UX-Verbesserungen sind erwünscht,
 * solange die Struktur wiedererkennbar bleibt).
 */
public class UserGroupFormDialog extends Dialog {

    private static final String NONE = "none";
    private static final String FIX = "fix";
    private static final String FACTOR = "factor";

    private final UserGroupService userGroupService;
    private final UserGroupEntity groupToEdit;

    private final TextField tfName = new TextField("Name");
    private final RadioButtonGroup<String> rgDiscountType = new RadioButtonGroup<>();
    private final NumberField nfDiscountFix = new NumberField("Rabatt (€)");
    private final NumberField nfDiscountFactorPercent = new NumberField("Rabatt (%)");
    private final MultiSelectComboBox<LocationEntity> selLocations = new MultiSelectComboBox<>("Standorte");
    private final MultiSelectComboBox<DeviceEntity> selDevices = new MultiSelectComboBox<>("Geräte");
    private final MultiSelectComboBox<ProgramEntity> selPrograms = new MultiSelectComboBox<>("Programme");

    public UserGroupFormDialog(UserGroupService userGroupService, LocationService locationService,
            DeviceService deviceService, ProgramService programService, UserGroupEntity groupToEdit,
            Runnable onSaved) {
        this.userGroupService = userGroupService;
        this.groupToEdit = groupToEdit;

        boolean editMode = groupToEdit != null;
        setHeaderTitle(editMode ? "Gruppe bearbeiten" : "Gruppe erstellen");
        setModal(true);
        setWidth("45em");

        this.tfName.setRequired(true);
        this.tfName.setWidthFull();

        this.rgDiscountType.setLabel("Rabattierung");
        this.rgDiscountType.setItems(NONE, FIX, FACTOR);
        this.rgDiscountType.setItemLabelGenerator(v -> switch (v) {
            case FIX -> "Fix";
            case FACTOR -> "Faktor";
            default -> "Keiner";
        });
        this.rgDiscountType.setValue(NONE);
        this.rgDiscountType.addValueChangeListener(e -> updateDiscountFieldVisibility());

        this.nfDiscountFix.setWidthFull();
        this.nfDiscountFix.setMin(0);
        this.nfDiscountFactorPercent.setWidthFull();
        this.nfDiscountFactorPercent.setMin(0);
        this.nfDiscountFactorPercent.setMax(100);

        this.selLocations.setItems(locationService.findAll());
        this.selLocations.setItemLabelGenerator(LocationEntity::getName);
        this.selLocations.setWidthFull();

        this.selDevices.setItems(deviceService.findAll());
        this.selDevices.setItemLabelGenerator(d -> d.getName() + " (" + d.getLocation().getName() + ")");
        this.selDevices.setWidthFull();

        this.selPrograms.setItems(programService.findAll());
        this.selPrograms.setItemLabelGenerator(ProgramEntity::getName);
        this.selPrograms.setWidthFull();

        FormLayout form = new FormLayout(this.tfName, this.rgDiscountType, this.nfDiscountFix,
                this.nfDiscountFactorPercent);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        add(form, new H3("Standorte"), this.selLocations, new H3("Geräte"), this.selDevices, new H3("Programme"),
                this.selPrograms);

        if (editMode) {
            this.tfName.setValue(groupToEdit.getName());
            switch (groupToEdit.getDiscountType()) {
                case FIX -> {
                    this.rgDiscountType.setValue(FIX);
                    this.nfDiscountFix.setValue(groupToEdit.getDiscountValue());
                }
                case FACTOR -> {
                    this.rgDiscountType.setValue(FACTOR);
                    this.nfDiscountFactorPercent.setValue(groupToEdit.getDiscountValue() * 100);
                }
                default -> this.rgDiscountType.setValue(NONE);
            }
            this.selLocations.setValue(Set.copyOf(userGroupService.findValidLocations(groupToEdit)));
            this.selDevices.setValue(Set.copyOf(userGroupService.findValidDevices(groupToEdit)));
            this.selPrograms.setValue(Set.copyOf(userGroupService.findValidPrograms(groupToEdit)));
        }
        updateDiscountFieldVisibility();

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button(editMode ? "Speichern" : "Erstellen", e -> save(onSaved));
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSave));
    }

    private void updateDiscountFieldVisibility() {
        String value = this.rgDiscountType.getValue();
        this.nfDiscountFix.setVisible(FIX.equals(value));
        this.nfDiscountFactorPercent.setVisible(FACTOR.equals(value));
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

        String type = this.rgDiscountType.getValue();
        if (FIX.equals(type) && this.nfDiscountFix.isEmpty()) {
            this.nfDiscountFix.setInvalid(true);
            this.nfDiscountFix.setErrorMessage("Bitte Rabatt eingeben.");
            valid = false;
        }
        if (FACTOR.equals(type) && this.nfDiscountFactorPercent.isEmpty()) {
            this.nfDiscountFactorPercent.setInvalid(true);
            this.nfDiscountFactorPercent.setErrorMessage("Bitte Rabatt eingeben.");
            valid = false;
        }

        if (!valid) {
            return;
        }

        DiscountType discountType;
        double discountValue;
        switch (type) {
            case FIX -> {
                discountType = DiscountType.FIX;
                discountValue = this.nfDiscountFix.getValue();
            }
            case FACTOR -> {
                discountType = DiscountType.FACTOR;
                discountValue = this.nfDiscountFactorPercent.getValue() / 100;
            }
            default -> {
                discountType = DiscountType.NONE;
                discountValue = 0d;
            }
        }

        try {
            UserGroupEntity group;
            if (this.groupToEdit == null) {
                group = this.userGroupService.create(this.tfName.getValue(), discountType, discountValue);
            } else {
                group = this.userGroupService.update(this.groupToEdit, this.tfName.getValue(), discountType,
                        discountValue);
            }
            this.userGroupService.setValidLocations(group,
                    this.selLocations.getValue().stream().map(LocationEntity::getId).collect(Collectors.toSet()));
            this.userGroupService.setValidDevices(group,
                    this.selDevices.getValue().stream().map(DeviceEntity::getId).collect(Collectors.toSet()));
            this.userGroupService.setValidPrograms(group,
                    this.selPrograms.getValue().stream().map(ProgramEntity::getId).collect(Collectors.toSet()));
        } catch (RuntimeException e) {
            Notification notification = Notification.show(
                    "Die Benutzergruppe konnte nicht gespeichert werden. " + e.getMessage(), 5000,
                    Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        close();
        onSaved.run();
    }
}
