package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;

/**
 * Modaler Dialog zum Anlegen/Bearbeiten eines Programms - fachlicher Nachfolger von
 * {@code Portal/.../components/ProgramWindow} (Alt-Portal, Testfall P12). Felder wie im
 * Alt-Fenster: Name, Aktiviert, Typ (Statisch=FIXED/Dynamisch=DYNAMIC) mit den jeweiligen
 * Preisfeldern (Preis bzw. Grundgebühr+Zeitpreis+Abrechnungsintervall), Maximaldauer, Freie
 * Zeit, Auto-Ende, Frühester Abbruch, freigegebene Benutzergruppen.
 */
public class ProgramFormDialog extends Dialog {

    private static final String STATIC = "static";
    private static final String DYNAMIC = "dynamic";

    private final ProgramService programService;
    private final ProgramEntity programToEdit;

    private final TextField tfName = new TextField("Name");
    private final Checkbox cbEnabled = new Checkbox("Aktiviert");
    private final RadioButtonGroup<String> rgType = new RadioButtonGroup<>();
    private final BigDecimalField bfPrice = new BigDecimalField("Preis");
    private final BigDecimalField bfFlagfall = new BigDecimalField("Grundgebühr");
    private final BigDecimalField bfRate = new BigDecimalField("Zeitpreis");
    private final ComboBox<TimeUnitType> cbTimeUnit = new ComboBox<>("Abr.-Intervall");
    private final DurationField maxDuration = new DurationField("Maximaldauer");
    private final DurationField freeDuration = new DurationField("Freie Zeit");
    private final Checkbox cbAutoEnd = new Checkbox("Auto-Ende");
    private final DurationField earliestAutoEnd = new DurationField("Frühester Abbruch");
    private final MultiSelectComboBox<UserGroupEntity> selGroups = new MultiSelectComboBox<>("Benutzergruppen");

    public ProgramFormDialog(ProgramService programService, UserGroupService userGroupService,
            ProgramEntity programToEdit, Runnable onSaved) {
        this.programService = programService;
        this.programToEdit = programToEdit;

        boolean editMode = programToEdit != null;
        setHeaderTitle(editMode ? "Programm bearbeiten" : "Programm erstellen");
        setModal(true);
        setWidth("45em");

        this.tfName.setRequired(true);
        this.tfName.setWidthFull();

        this.rgType.setLabel("Typ");
        this.rgType.setItems(STATIC, DYNAMIC);
        this.rgType.setItemLabelGenerator(v -> STATIC.equals(v) ? "Statisch" : "Dynamisch");
        this.rgType.setValue(STATIC);
        this.rgType.addValueChangeListener(e -> updateTypeFieldVisibility());

        this.bfPrice.setWidthFull();
        this.bfFlagfall.setWidthFull();
        this.bfRate.setWidthFull();

        this.cbTimeUnit.setItems(TimeUnitType.values());
        this.cbTimeUnit.setItemLabelGenerator(ProgramFormDialog::timeUnitLabel);
        this.cbTimeUnit.setValue(TimeUnitType.SECONDS);
        this.cbTimeUnit.setWidthFull();
        this.cbTimeUnit.setHelperText("Nach jeder verstrichenen Einheit wird der Zeitpreis fällig.");

        this.maxDuration.setValue(Duration.ofMinutes(30));
        this.freeDuration.setValue(Duration.ZERO);
        this.earliestAutoEnd.setValue(Duration.ZERO);
        this.cbAutoEnd.setValue(true);

        this.selGroups.setItems(userGroupService.findAll());
        this.selGroups.setItemLabelGenerator(UserGroupEntity::getName);
        this.selGroups.setWidthFull();

        FormLayout form = new FormLayout(this.tfName, this.cbEnabled, this.rgType, this.bfPrice, this.bfFlagfall,
                this.bfRate, this.cbTimeUnit, this.maxDuration, this.freeDuration, this.cbAutoEnd,
                this.earliestAutoEnd);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30em", 2));

        add(form, new H3("Benutzergruppen"), this.selGroups);

        if (editMode) {
            this.tfName.setValue(programToEdit.getName());
            this.cbEnabled.setValue(programToEdit.isEnabled());
            this.cbAutoEnd.setValue(programToEdit.isAutoEnd());
            this.maxDuration.setValue(Duration.ofSeconds(programToEdit.getMaxDurationSeconds()));
            this.freeDuration.setValue(Duration.ofSeconds(programToEdit.getFreeDurationSeconds()));
            this.earliestAutoEnd.setValue(Duration.ofSeconds(programToEdit.getEarliestAutoEndSeconds()));
            if (programToEdit.getType() == ProgramType.DYNAMIC) {
                this.rgType.setValue(DYNAMIC);
                this.bfFlagfall.setValue(programToEdit.getFlagfall());
                this.bfRate.setValue(programToEdit.getRate());
                this.cbTimeUnit.setValue(programToEdit.getTimeUnit() == null ? TimeUnitType.SECONDS
                        : programToEdit.getTimeUnit());
            } else {
                this.rgType.setValue(STATIC);
                this.bfPrice.setValue(programToEdit.getFlagfall());
            }
            this.selGroups.setValue(Set.copyOf(programToEdit.getValidUserGroups()));
        } else {
            this.cbEnabled.setValue(true);
        }
        updateTypeFieldVisibility();

        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSave = new Button(editMode ? "Speichern" : "Erstellen", e -> save(onSaved));
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSave));
    }

    private void updateTypeFieldVisibility() {
        boolean dynamic = DYNAMIC.equals(this.rgType.getValue());
        this.bfPrice.setVisible(!dynamic);
        this.bfFlagfall.setVisible(dynamic);
        this.bfRate.setVisible(dynamic);
        this.cbTimeUnit.setVisible(dynamic);
    }

    private static String timeUnitLabel(TimeUnitType unit) {
        return switch (unit) {
            case HOURS -> "Stunden";
            case MINUTES -> "Minuten";
            case SECONDS -> "Sekunden";
        };
    }

    private void save(Runnable onSaved) {
        boolean valid = true;
        valid &= requireText(this.tfName, "Bitte Name eingeben.");
        boolean dynamic = DYNAMIC.equals(this.rgType.getValue());
        if (dynamic) {
            valid &= requireBigDecimal(this.bfFlagfall, "Bitte Grundgebühr eingeben.");
            valid &= requireBigDecimal(this.bfRate, "Bitte Zeitpreis eingeben.");
        } else {
            valid &= requireBigDecimal(this.bfPrice, "Bitte Preis eingeben.");
        }
        valid &= requireDuration(this.maxDuration, "Der Wert muss größer als 0 sein.", true);
        valid &= requireDuration(this.freeDuration, "Der Wert darf nicht negativ sein.", false);
        valid &= requireDuration(this.earliestAutoEnd, "Der Wert darf nicht negativ sein.", false);

        if (!valid) {
            return;
        }

        ProgramType type = dynamic ? ProgramType.DYNAMIC : ProgramType.FIXED;
        BigDecimal flagfall = dynamic ? this.bfFlagfall.getValue() : this.bfPrice.getValue();
        BigDecimal rate = dynamic ? this.bfRate.getValue() : null;
        TimeUnitType timeUnit = dynamic ? this.cbTimeUnit.getValue() : null;

        try {
            if (this.programToEdit == null) {
                this.programService.create(this.tfName.getValue(), type, flagfall, rate, timeUnit,
                        this.maxDuration.getValue(), this.freeDuration.getValue(), this.cbAutoEnd.getValue(),
                        this.earliestAutoEnd.getValue(), this.cbEnabled.getValue(), this.selGroups.getValue());
            } else {
                this.programService.update(this.programToEdit, this.tfName.getValue(), type, flagfall, rate,
                        timeUnit, this.maxDuration.getValue(), this.freeDuration.getValue(),
                        this.cbAutoEnd.getValue(), this.earliestAutoEnd.getValue(), this.cbEnabled.getValue(),
                        this.selGroups.getValue());
            }
        } catch (RuntimeException e) {
            Notification notification = Notification.show(
                    "Das Programm konnte nicht gespeichert werden. " + e.getMessage(), 5000,
                    Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        close();
        onSaved.run();
    }

    private static boolean requireText(TextField field, String message) {
        if (field.isEmpty()) {
            field.setInvalid(true);
            field.setErrorMessage(message);
            return false;
        }
        field.setInvalid(false);
        return true;
    }

    private static boolean requireBigDecimal(BigDecimalField field, String message) {
        if (field.isEmpty()) {
            field.setInvalid(true);
            field.setErrorMessage(message);
            return false;
        }
        field.setInvalid(false);
        return true;
    }

    private static boolean requireDuration(DurationField field, String message, boolean mustBePositive) {
        Duration value = field.getValue();
        if (value == null || (mustBePositive ? value.isZero() || value.isNegative() : value.isNegative())) {
            field.setInvalid(true);
            field.setErrorMessage(message);
            return false;
        }
        field.setInvalid(false);
        return true;
    }

    /**
     * Kleines zusammengesetztes Feld (Zahl + Zeiteinheit) für Dauer-Eingaben - Nachbildung
     * der entsprechenden {@code CssLayout}-Gruppen aus {@code ProgramWindow} (Alt-Portal:
     * Maximaldauer/Freie Zeit/Frühester Abbruch, jeweils Zahl + Stunden/Minuten/Sekunden).
     */
    private static final class DurationField extends HorizontalLayout {

        private final IntegerField amount = new IntegerField();
        private final ComboBox<TimeUnitType> unit = new ComboBox<>();

        DurationField(String label) {
            setWidthFull();
            getElement().setAttribute("aria-label", label);
            this.amount.setLabel(label);
            this.amount.setWidth("60%");
            this.unit.setItems(TimeUnitType.values());
            this.unit.setItemLabelGenerator(u -> switch (u) {
                case HOURS -> "h";
                case MINUTES -> "min";
                case SECONDS -> "s";
            });
            this.unit.setValue(TimeUnitType.SECONDS);
            this.unit.setWidth("40%");
            this.unit.getStyle().set("align-self", "flex-end");
            add(this.amount, this.unit);
        }

        void setValue(Duration duration) {
            long seconds = duration.getSeconds();
            if (seconds != 0 && seconds % 3600 == 0) {
                this.amount.setValue((int) (seconds / 3600));
                this.unit.setValue(TimeUnitType.HOURS);
            } else if (seconds != 0 && seconds % 60 == 0) {
                this.amount.setValue((int) (seconds / 60));
                this.unit.setValue(TimeUnitType.MINUTES);
            } else {
                this.amount.setValue((int) seconds);
                this.unit.setValue(TimeUnitType.SECONDS);
            }
        }

        Duration getValue() {
            if (this.amount.isEmpty() || this.unit.isEmpty()) {
                return null;
            }
            return switch (this.unit.getValue()) {
                case HOURS -> Duration.ofHours(this.amount.getValue());
                case MINUTES -> Duration.ofMinutes(this.amount.getValue());
                case SECONDS -> Duration.ofSeconds(this.amount.getValue());
            };
        }

        void setInvalid(boolean invalid) {
            this.amount.setInvalid(invalid);
        }

        void setErrorMessage(String message) {
            this.amount.setErrorMessage(message);
        }
    }
}
