package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.List;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.component.PortalButtons;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;

/**
 * Dialog "Verfallene Ausführungsaufträge" (Phase 3 AP4, siehe docs/kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/ExpiredExecutionsWindow}
 * (Alt-Portal): listet nicht abgerechnete, aber bereits abgelaufene Ausführungen eines
 * Benutzers auf (siehe {@link ExecutionService#getExpiredExecutions}) - sie zählen bereits
 * mit ihrem Maximalpreis gegen das Guthaben des Benutzers ({@code CreditService#getCredit}),
 * ohne dass es dafür schon einen Buchungssatz in {@code credit_accounting} gibt (typischerweise
 * ein Zeichen für einen früheren Client-Fehler, siehe Erklärungstext unten - 1:1 aus dem
 * Alt-Fenster übernommen). Pro Zeile "Abrechnen" ({@link ExecutionService#finishExecution},
 * entspricht {@code User#payExecution} + {@code Execution#finish}) oder "Löschen"
 * ({@link ExecutionService#delete}, entspricht {@code Execution#delete}), zusätzlich "Alle
 * abrechnen" als Sammelaktion.
 */
public class ExpiredExecutionsDialog extends Dialog {

    private final ExecutionService executionService;
    private final UserEntity user;
    private final Runnable onChanged;

    private final Grid<ExecutionEntity> grid = new Grid<>();

    public ExpiredExecutionsDialog(ExecutionService executionService, UserEntity user, Runnable onChanged) {
        this.executionService = executionService;
        this.user = user;
        this.onChanged = onChanged;

        setHeaderTitle("Verfallene Ausführungsaufträge von " + user.getName());
        setModal(true);
        setWidth("60em");
        // UI-Redesign v2: dasselbe Schließen-Kreuz wie in den Formular-Dialogen (dieser Dialog
        // erbt bewusst nicht von AbstractFormDialog, siehe dortiges Klassen-Javadoc).
        AbstractFormDialog.addCloseButton(this);

        Paragraph explanation = new Paragraph(
                "Diese Ausführungsaufträge wurden gestartet, jedoch nie beendet, möglicherweise durch einen Fehler "
                        + "im elwaClient. Der höchstmögliche Betrag von laufenden Ausführung wird beim Berechnen "
                        + "des Guthabens eines Benutzers zwar bereits berücksichtigt, jedoch existiert für nicht "
                        + "korrekt beendete Ausführungen kein Eintrag im Guthaben-Konto eines Benutzers.");
        explanation.addClassName("small");

        // Issue #49: Doppelklick-Schutz auf dem geldbewegenden Primär-Button. Wieder aktiv nach
        // dem Roundtrip - der Dialog bleibt offen (er schließt bewusst auch bei leerer Liste
        // nicht, siehe loadData), und ohne das Zurücksetzen wäre der Knopf nach dem ersten Klick
        // bis zum Neuöffnen tot (siehe PortalButtons).
        Button btnFinishAll = PortalButtons.onAction(new Button("Alle abrechnen"), this::finishAll);

        this.grid.setHeight("22em");
        this.grid.setWidthFull();
        this.grid.addColumn(e -> PortalFormats.dateTime(e.getStart())).setHeader("Startdatum");
        this.grid.addColumn(e -> e.getDevice().getName() + " (" + e.getDevice().getLocation().getName() + ")")
                .setHeader("Gerät");
        this.grid.addColumn(e -> e.getProgram().getName()).setHeader("Programm");
        this.grid.addColumn(e -> PortalFormats.currency(this.executionService.getPrice(e)))
                .setHeader("Fälliger Betrag");
        this.grid.addComponentColumn(this::rowButtons).setHeader("").setFlexGrow(0).setAutoWidth(true);

        add(explanation, new HorizontalLayout(btnFinishAll), this.grid);

        loadData();
    }

    private HorizontalLayout rowButtons(ExecutionEntity execution) {
        // Issue #49: Doppelklick-Schutz auf dem geldbewegenden "Abrechnen"-Knopf (samt
        // Zurücksetzen danach, siehe PortalButtons).
        Button btnFinish = PortalButtons.onAction(new Button("Abrechnen"), () -> finish(execution));
        btnFinish.addThemeVariants(ButtonVariant.LUMO_SMALL);

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH), e -> confirmDelete(execution));
        btnDelete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout buttons = new HorizontalLayout(btnFinish, btnDelete);
        // Enger Abstand wie in den Listenansichten: die v2-Bedienelemente sind groesser, der
        // Lumo-Standardabstand von 1rem sprengte die Aktionsspalte.
        buttons.getThemeList().add("spacing-xs");
        return buttons;
    }

    private void finish(ExecutionEntity execution) {
        this.executionService.finishExecution(execution);
        loadData();
        this.onChanged.run();
    }

    /**
     * Issue #49: Das Löschen eines abrechnungsrelevanten Datensatzes läuft - wie alle anderen
     * Löschpfade im Portal - über eine ausdrückliche Bestätigung (kein Einzelklick-Löschen).
     */
    private void confirmDelete(ExecutionEntity execution) {
        ConfirmDeleteDialog.show("Ausführung löschen",
                "Möchten Sie diesen nicht abgerechneten Ausführungsauftrag wirklich löschen?",
                () -> delete(execution));
    }

    private void delete(ExecutionEntity execution) {
        this.executionService.delete(execution);
        loadData();
        this.onChanged.run();
    }

    private void finishAll() {
        for (ExecutionEntity execution : this.executionService.getExpiredExecutions(this.user)) {
            this.executionService.finishExecution(execution);
        }
        loadData();
        this.onChanged.run();
    }

    private void loadData() {
        // Bewusst KEIN automatisches Schließen bei leerer Liste (1:1 wie
        // ExpiredExecutionsWindow: die Tabelle bleibt nach dem letzten "Löschen"/"Abrechnen"
        // einfach leer, der Administrator schließt selbst).
        this.grid.setItems(this.executionService.getExpiredExecutions(this.user));
        // autoWidth misst erst, wenn Zellinhalt gerendert ist - der kommt aber gerade eben erst.
        this.grid.recalculateColumnWidths();
    }
}
