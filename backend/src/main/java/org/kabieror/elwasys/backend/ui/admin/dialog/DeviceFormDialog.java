package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.FormValidation;

/**
 * Modaler Dialog zum Anlegen/Bearbeiten eines Geräts - fachlicher Nachfolger von
 * {@code Portal/.../components/DeviceWindow} (Alt-Portal, Testfälle P10/P11). Felder wie im
 * Alt-Fenster: Name, Position, Standort, fhem-Name/-Switch-Name/-Power-Name, deCONZ-UUID
 * (beide Gateways bleiben laut Auftraggeber unterstützt, siehe docs/kb/05-migration-plan.md),
 * Auto-Ende-Schwellwert/-Wartezeit, Aktiviert, zugeordnete Programme, freigegebene
 * Benutzergruppen.
 *
 * <p>Bewusste Vereinfachung gegenüber dem Alt-Fenster: das dortige "neuen Standort per
 * Geräte-Dialog anlegen" (inkl. automatischem Aufräumen unbenutzter Standorte beim
 * Schließen) entfällt - Standorte haben seit AP1 eine eigene Verwaltung
 * ({@code AdminLocationsView}), das macht diesen Nebenpfad überflüssig statt einer
 * Funktionslücke (siehe docs/kb/05-migration-plan.md, "Entscheidungen", Gestaltungsrahmen
 * Portal-Neubau).
 */
public class DeviceFormDialog extends AbstractFormDialog {

    private final DeviceService deviceService;
    private final DeviceEntity deviceToEdit;

    private final TextField tfName = new TextField("Name");
    private final ComboBox<Integer> cbPosition = new ComboBox<>("Position");
    private final ComboBox<LocationEntity> cbLocation = new ComboBox<>("Standort");
    private final TextField tfFhemName = new TextField("Fhem Name");
    private final TextField tfFhemSwitchName = new TextField("Fhem Switch Name");
    private final TextField tfFhemPowerName = new TextField("Fhem Power Name");
    private final TextField tfDeconzUuid = new TextField("deCONZ UUID");
    private final NumberField nfAutoEndPowerThreshold = new NumberField("Auto-Ende Schwellwert (W)");
    private final IntegerField ifAutoEndWaitTime = new IntegerField("Auto-Ende Wartezeit (s)");
    private final Checkbox cbEnabled = new Checkbox("Aktiviert");
    private final MultiSelectComboBox<ProgramEntity> selPrograms = new MultiSelectComboBox<>("Programme");
    private final MultiSelectComboBox<UserGroupEntity> selGroups = new MultiSelectComboBox<>("Benutzergruppen");

    public DeviceFormDialog(DeviceService deviceService, LocationService locationService,
            ProgramService programService, UserGroupService userGroupService, DeviceEntity deviceToEdit,
            Runnable onSaved) {
        super(entityTitle("Gerät", deviceToEdit != null), "45em");
        this.deviceService = deviceService;
        this.deviceToEdit = deviceToEdit;

        boolean editMode = deviceToEdit != null;

        this.tfName.setRequired(true);
        this.tfName.setWidthFull();

        this.cbPosition.setRequired(true);
        this.cbPosition.setItems(List.of(1, 2, 3, 4));
        this.cbPosition.setHelperText("Die Gerätenummer, unter der das Gerät im Terminal angezeigt wird.");
        this.cbPosition.setWidthFull();

        this.cbLocation.setRequired(true);
        this.cbLocation.setItems(locationService.findAll());
        this.cbLocation.setItemLabelGenerator(LocationEntity::getName);
        this.cbLocation.setWidthFull();

        this.tfFhemName.setRequired(true);
        this.tfFhemName.setHelperText("Der Name des Gerätes im Fhem-Server.");
        this.tfFhemName.setWidthFull();

        this.tfFhemSwitchName.setRequired(true);
        this.tfFhemSwitchName.setHelperText("Der Name des Schalters für das Gerät im Fhem-Server.");
        this.tfFhemSwitchName.setWidthFull();

        this.tfFhemPowerName.setRequired(true);
        this.tfFhemPowerName.setHelperText("Der Name des Leistungs-Messungs-Kanals im Fhem-Server.");
        this.tfFhemPowerName.setWidthFull();

        this.tfDeconzUuid.setHelperText("Die UUID des Zigbee-Geräts im deCONZ-Gateway (falls verwendet).");
        this.tfDeconzUuid.setWidthFull();

        this.nfAutoEndPowerThreshold.setRequired(true);
        this.nfAutoEndPowerThreshold.setHelperText(
                "Schwellwert der abgenommenen Leistung in Watt, bei dessen Unterschreitung das laufende Programm "
                        + "beendet wird.");
        this.nfAutoEndPowerThreshold.setMin(0);
        this.nfAutoEndPowerThreshold.setWidthFull();

        this.ifAutoEndWaitTime.setRequired(true);
        this.ifAutoEndWaitTime.setHelperText(
                "Wartezeit nach Unterschreiten des Schwellwerts, bevor das Programm automatisch beendet wird.");
        this.ifAutoEndWaitTime.setMin(0);
        this.ifAutoEndWaitTime.setWidthFull();

        this.selPrograms.setItems(programService.findAll());
        this.selPrograms.setItemLabelGenerator(ProgramEntity::getName);
        this.selPrograms.setWidthFull();

        this.selGroups.setItems(userGroupService.findAll());
        this.selGroups.setItemLabelGenerator(UserGroupEntity::getName);
        this.selGroups.setWidthFull();

        FormLayout form = new FormLayout(this.tfName, this.cbPosition, this.cbLocation, this.tfFhemName,
                this.tfFhemSwitchName, this.tfFhemPowerName, this.tfDeconzUuid, this.nfAutoEndPowerThreshold,
                this.ifAutoEndWaitTime, this.cbEnabled);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30em", 2));

        add(form, new H3("Programme"), this.selPrograms, new H3("Benutzergruppen"), this.selGroups);

        if (editMode) {
            this.tfName.setValue(deviceToEdit.getName());
            this.cbPosition.setValue(deviceToEdit.getPosition());
            this.cbLocation.setValue(deviceToEdit.getLocation());
            this.tfFhemName.setValue(deviceToEdit.getFhemName());
            this.tfFhemSwitchName.setValue(deviceToEdit.getFhemSwitchName());
            this.tfFhemPowerName.setValue(deviceToEdit.getFhemPowerName());
            this.tfDeconzUuid.setValue(
                    deviceToEdit.getDeconzUuid() == null ? "" : deviceToEdit.getDeconzUuid());
            this.nfAutoEndPowerThreshold.setValue((double) deviceToEdit.getAutoEndPowerThreshold());
            this.ifAutoEndWaitTime.setValue(deviceToEdit.getAutoEndWaitTimeSeconds());
            this.cbEnabled.setValue(deviceToEdit.isEnabled());
            this.selPrograms.setValue(Set.copyOf(deviceToEdit.getPrograms()));
            this.selGroups.setValue(Set.copyOf(deviceToEdit.getValidUserGroups()));
        } else {
            this.cbEnabled.setValue(true);
        }

        addFooterActions(saveCaption(editMode), () -> save(onSaved));
    }

    private void save(Runnable onSaved) {
        // Bewusst bitweises "&=" statt des kurzschließenden "&&": ALLE Felder sollen geprüft und
        // markiert werden, nicht nur bis zum ersten Fehler - sonst müsste der Administrator die
        // Fehler eines Formulars einzeln nacheinander aufdecken.
        boolean valid = true;
        valid &= FormValidation.require(this.tfName, "Bitte Name eingeben.");
        valid &= FormValidation.require(this.cbPosition, "Bitte Position auswählen.");
        valid &= FormValidation.require(this.cbLocation, "Bitte Standort auswählen.");
        valid &= FormValidation.require(this.tfFhemName, "Bitte Namen angeben.");
        valid &= FormValidation.require(this.tfFhemSwitchName, "Bitte Namen angeben.");
        valid &= FormValidation.require(this.tfFhemPowerName, "Bitte Namen angeben.");
        valid &= FormValidation.require(this.nfAutoEndPowerThreshold, "Bitte Schwellwert eingeben.");
        valid &= FormValidation.require(this.ifAutoEndWaitTime, "Bitte Wartezeit eingeben.");

        if (!valid) {
            return;
        }

        try {
            if (this.deviceToEdit == null) {
                this.deviceService.create(this.tfName.getValue(), this.cbPosition.getValue(),
                        this.cbLocation.getValue(), this.tfFhemName.getValue(), this.tfFhemSwitchName.getValue(),
                        this.tfFhemPowerName.getValue(), this.tfDeconzUuid.getValue(),
                        this.nfAutoEndPowerThreshold.getValue().floatValue(),
                        Duration.ofSeconds(this.ifAutoEndWaitTime.getValue()), this.cbEnabled.getValue(),
                        this.selPrograms.getValue(), this.selGroups.getValue());
            } else {
                this.deviceService.update(this.deviceToEdit, this.tfName.getValue(), this.cbPosition.getValue(),
                        this.cbLocation.getValue(), this.tfFhemName.getValue(), this.tfFhemSwitchName.getValue(),
                        this.tfFhemPowerName.getValue(), this.tfDeconzUuid.getValue(),
                        this.nfAutoEndPowerThreshold.getValue().floatValue(),
                        Duration.ofSeconds(this.ifAutoEndWaitTime.getValue()), this.cbEnabled.getValue(),
                        this.selPrograms.getValue(), this.selGroups.getValue());
            }
        } catch (RuntimeException e) {
            showFailure("Das Gerät konnte nicht gespeichert werden.", e);
            return;
        }

        close();
        onSaved.run();
    }
}
