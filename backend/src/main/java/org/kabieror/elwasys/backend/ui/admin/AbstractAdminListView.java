package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.shared.Registration;
import java.util.List;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.component.ListFilterField;
import org.kabieror.elwasys.backend.ui.component.Notifications;
import org.kabieror.elwasys.backend.ui.component.PortalGrids;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Das gemeinsame Gerüst der Admin-Listenansichten (Standorte, Geräte, Programme, Benutzergruppen,
 * Benutzer): Kopfzeile mit "Neu"-Schaltfläche, Tabelle über die volle Fläche, Aktionsspalte mit
 * Bearbeiten/Löschen, Lösch-Bestätigung samt Behandlung von {@link EntityInUseException} sowie
 * die An-/Abmeldung am {@link UiBroadcaster} für seitenübergreifende Live-Updates.
 *
 * <p>Herausgezogen im Rahmen der finalen Review (R3c, Issue #92): die fünf Ansichten trugen diese
 * ~70 Zeilen jeweils als eigene Kopie. Das ist rein strukturell - jede Ansicht liefert ihre
 * Besonderheiten weiterhin selbst über die abstrakten Methoden, das sichtbare Verhalten bleibt
 * unverändert.
 *
 * <p><b>Seit UI-Redesign v2 AP4</b> (siehe docs/kb/05-migration-plan.md und
 * docs/specs/0002-ui-design/v2/MAPPING.md, Abschnitt "Listen"): die Toolbar trägt zusätzlich ein
 * clientseitiges Filterfeld ({@link ListFilterField}), das auf den bereits geladenen Zeilen
 * filtert - alle Unterklassen laden ihre Daten eager ({@link #findAll()} liefert eine
 * vollständige {@link List}), ein serverseitiges Nachreichen des Filters in eine Query entfällt
 * deshalb. Wonach gefiltert wird, legt jede Unterklasse über {@link #filterableText(Object)} fest.
 *
 * @param <T> Der Entitätstyp, den die Ansicht auflistet.
 */
public abstract class AbstractAdminListView<T> extends VerticalLayout {

    private final UiBroadcaster broadcaster;

    private final Grid<T> grid = new Grid<>();

    private final ListFilterField<T> filterField;

    private Registration broadcasterRegistration;

    /**
     * @param title      Überschrift der Ansicht (zugleich Beschriftung der Kopfzeile).
     * @param cssClass   CSS-Klasse der Ansicht (Selektor-Anker für die E2E-Tests, siehe
     *                   docs/kb/06-ui-tests.md).
     * @param newButton  Beschriftung der Anlege-Schaltfläche, oder {@code null}, wenn die Ansicht
     *                   kein Anlegen anbietet.
     * @param broadcaster Der Verteiler für Live-Updates zwischen Sessions.
     */
    protected AbstractAdminListView(String title, String cssClass, String newButton, UiBroadcaster broadcaster) {
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName(cssClass);

        H2 heading = new H2(title);

        // Die zugängliche Beschriftung aus dem Titel ableiten: der ist in jeder Listenansicht der
        // Plural der aufgelisteten Sache ("Geräte" -> "Geräte filtern"), ein eigener
        // Konstruktor-Parameter wäre in allen fünf Unterklassen nur dessen Wiederholung.
        this.filterField = new ListFilterField<>(title + " filtern");

        HorizontalLayout toolbar = new HorizontalLayout(heading, this.filterField);
        toolbar.addClassName("list-toolbar");
        if (newButton != null) {
            Button btnNew = new Button(newButton, new Icon(VaadinIcon.PLUS), e -> openCreateDialog());
            btnNew.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            toolbar.add(btnNew);
        }
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, heading);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

        this.grid.setSizeFull();

        add(toolbar, this.grid);
        setFlexGrow(1, this.grid);
    }

    /**
     * Baut Spalten und Daten auf. Muss vom Unterklassen-Konstruktor als LETZTER Schritt gerufen
     * werden: die Spalten greifen auf Felder der Unterklasse zu, die zum Zeitpunkt des
     * Basis-Konstruktors noch nicht gesetzt sind.
     */
    protected void initGrid() {
        configureColumns(this.grid);
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setAutoWidth(true);
        // Erst binden, dann laden: setGridItems() legt den Filter danach auf jeden neuen
        // Datenbestand.
        this.filterField.bindTo(this.grid, this::filterableText);
        loadData();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.broadcasterRegistration = this.broadcaster.register(attachEvent.getUI(), event -> {
            if (isRelevantChange(event)) {
                loadData();
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (this.broadcasterRegistration != null) {
            this.broadcasterRegistration.remove();
            this.broadcasterRegistration = null;
        }
        super.onDetach(detachEvent);
    }

    /**
     * Lädt die Tabelle neu.
     */
    protected void loadData() {
        setGridItems(findAll());
    }

    /**
     * Setzt die Grid-Daten und wendet den aktuellen Filtertext erneut an. Unterklassen, die
     * {@link #loadData()} überschreiben (z. B. {@code AdminUsersView} wegen der gebündelt
     * vorberechneten Guthaben-Spalte), rufen diese Methode statt {@code getGrid().setItems(...)}
     * auf - sonst würde ein Neuladen über den {@link UiBroadcaster} den eingegebenen Filtertext
     * unbemerkt verwerfen (UI-Redesign v2 AP4).
     */
    protected void setGridItems(List<T> items) {
        PortalGrids.setItems(this.grid, items);
        this.filterField.reapply();
    }

    protected Grid<T> getGrid() {
        return this.grid;
    }

    /**
     * Die fachspezifischen Spalten (ohne die Aktionsspalte, die die Basisklasse anhängt).
     */
    protected abstract void configureColumns(Grid<T> grid);

    /**
     * Die anzuzeigenden Datensätze.
     */
    protected abstract List<T> findAll();

    /**
     * Durchsuchbarer Text einer Zeile für das Filterfeld (UI-Redesign v2 AP4, siehe
     * docs/specs/0002-ui-design/v2/MAPPING.md, Abschnitt "Listen"): deckt die sichtbaren
     * Textspalten der jeweiligen Ansicht ab. Groß-/Kleinschreibung und Leerraum spielen keine
     * Rolle - {@link ListFilterField} normalisiert beide Seiten des Vergleichs bereits. Damit
     * Spaltentext und Suchtext nicht auseinanderdriften, sollen beide dieselbe Beschriftungs-
     * bzw. Formatierungsmethode rufen (siehe {@code AdminDevicesView#statusLabel}).
     */
    protected abstract String filterableText(T item);

    /**
     * Ob ein über den {@link UiBroadcaster} verteiltes Ereignis diese Ansicht betrifft.
     */
    protected abstract boolean isRelevantChange(Object event);

    /**
     * Öffnet den Dialog zum Anlegen. Wird nur gerufen, wenn die Ansicht eine Anlege-Schaltfläche
     * hat; sonst genügt eine leere Implementierung.
     */
    protected abstract void openCreateDialog();

    /**
     * Öffnet den Dialog zum Bearbeiten des übergebenen Datensatzes.
     */
    protected abstract void openEditDialog(T item);

    /**
     * Löscht den Datensatz. Eine {@link EntityInUseException} wird von der Basisklasse als
     * Fehlermeldung angezeigt und bricht das Löschen ab.
     */
    protected abstract void delete(T item) throws EntityInUseException;

    /**
     * Titel des Lösch-Bestätigungsdialogs, z. B. "Gerät löschen".
     */
    protected abstract String deleteDialogTitle();

    /**
     * Rückfragetext des Lösch-Bestätigungsdialogs für diesen Datensatz.
     */
    protected abstract String deleteDialogQuestion(T item);

    /**
     * Die Aktionsspalte. Unterklassen mit zusätzlichen Aktionen überschreiben sie und ergänzen
     * das Ergebnis von {@code super.actionButtons(item)}.
     */
    protected HorizontalLayout actionButtons(T item) {
        Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
        btnEdit.setTooltipText("Bearbeiten");
        btnEdit.addClickListener(e -> openEditDialog(item));

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(item));

        HorizontalLayout buttons = new HorizontalLayout(btnEdit, btnDelete);
        // Enger Abstand: die Bedienelemente sind mit dem UI-Redesign v2 groesser geworden
        // (--lumo-size-*), der Lumo-Standardabstand von 1rem zwischen reinen Icon-Knoepfen
        // sprengte damit die Aktionsspalte - der Loeschen-Knopf wurde abgeschnitten.
        buttons.getThemeList().add("spacing-xs");
        return buttons;
    }

    /**
     * Fragt vor dem Löschen zurück und zeigt eine belegte Entität als Fehlermeldung an, statt sie
     * zu löschen (Issue #38: ein belegtes Gerät würde sonst weiter Guthaben belasten).
     */
    protected void confirmDelete(T item) {
        ConfirmDeleteDialog.show(deleteDialogTitle(), deleteDialogQuestion(item), () -> {
            try {
                delete(item);
            } catch (EntityInUseException e) {
                Notifications.showError(e.getMessage());
                return;
            }
            loadData();
        });
    }
}
