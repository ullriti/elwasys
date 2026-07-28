package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.LocationFormDialog;
import org.kabieror.elwasys.backend.ui.admin.dialog.TerminalTokenDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Standortverwaltung (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../components/LocationWindow} (Alt-Portal, Testfall P14), jetzt als
 * eigener Menüpunkt statt eines Dashboard-Dialogs (siehe {@code AdminLayout}-Javadoc: vom
 * Auftraggeber gewünschte UX-Verbesserung, keine Funktionsänderung). Ergänzt um Anlegen/
 * Löschen, die es im Alt-Fenster mangels eigener Ansicht so nicht gab.
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * einen Standort anlegt, bearbeitet oder löscht. Gerüst und Lösch-Ablauf kommen aus
 * {@link AbstractAdminListView} (Issue #92).
 *
 * <p><b>Terminal-Tokens</b>: je Zeile führt eine eigene Aktion in die Token-Verwaltung des
 * Standorts ({@link TerminalTokenDialog}) - ein Standort ist genau die Klammer, unter der ein
 * Terminal-Token gilt, deshalb sitzt die Verwaltung hier statt in einem eigenen Menüpunkt.
 */
@Route(value = "admin/locations", layout = AdminLayout.class)
@PageTitle("Standorte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminLocationsView extends AbstractAdminListView<LocationEntity> {

    private final LocationService locationService;
    private final UserGroupService userGroupService;
    private final TerminalTokenService terminalTokenService;

    public AdminLocationsView(LocationService locationService, UserGroupService userGroupService,
            TerminalTokenService terminalTokenService, UiBroadcaster broadcaster) {
        super("Standorte", "admin-locations-view", "Neu", broadcaster);
        this.locationService = locationService;
        this.userGroupService = userGroupService;
        this.terminalTokenService = terminalTokenService;
        initGrid();
    }

    @Override
    protected void configureColumns(Grid<LocationEntity> grid) {
        grid.addColumn(LocationEntity::getName).setHeader("Name").setSortable(true);
        grid.addColumn(AdminLocationsView::userGroupCountLabel).setHeader("Benutzergruppen");
        grid.addColumn(AdminLocationsView::offlineMaxDurationLabel).setHeader("Offline-Maximaldauer");
    }

    /**
     * Beschriftungen der beiden abgeleiteten Spalten. Eigene Methoden, damit Spaltentext und
     * Suchtext ({@link #filterableText}) dieselbe Quelle haben - sonst fände der Filter eine später
     * geänderte Darstellung nicht mehr, ohne dass es auffiele.
     */
    private static String userGroupCountLabel(LocationEntity location) {
        return String.valueOf(location.getValidUserGroups().size());
    }

    private static String offlineMaxDurationLabel(LocationEntity location) {
        return location.getOfflineMaxDurationMinutes() + " min";
    }

    @Override
    protected List<LocationEntity> findAll() {
        return this.locationService.findAll();
    }

    /**
     * Suchtext für das Filterfeld (UI-Redesign v2 AP4): Name, Anzahl Benutzergruppen und
     * Offline-Maximaldauer - dieselben Spalten wie {@link #configureColumns}.
     */
    @Override
    protected String filterableText(LocationEntity location) {
        return location.getName() + " " + userGroupCountLabel(location) + " " + offlineMaxDurationLabel(location);
    }

    @Override
    protected boolean isRelevantChange(Object event) {
        return event instanceof LocationChangedEvent;
    }

    @Override
    protected HorizontalLayout actionButtons(LocationEntity location) {
        HorizontalLayout buttons = super.actionButtons(location);

        Button btnTokens = new Button(new Icon(VaadinIcon.KEY));
        btnTokens.setTooltipText("Terminal-Tokens verwalten");
        btnTokens.addClickListener(e -> openTokenDialog(location));

        // Wie in AdminUsersView: die zusätzliche Aktion sitzt zwischen "Bearbeiten" und dem
        // "Löschen" der Basisklasse, damit die zerstörende Aktion überall ganz rechts steht.
        buttons.addComponentAtIndex(1, btnTokens);
        return buttons;
    }

    /**
     * Je Klick ein frischer Dialog: das einmalig angezeigte Klartext-Token darf einen einmal
     * geschlossenen Dialog nicht überleben (siehe {@link TerminalTokenDialog}).
     */
    private void openTokenDialog(LocationEntity location) {
        new TerminalTokenDialog(this.terminalTokenService, location).open();
    }

    @Override
    protected void openCreateDialog() {
        new LocationFormDialog(this.locationService, this.userGroupService, null, this::loadData).open();
    }

    @Override
    protected void openEditDialog(LocationEntity location) {
        new LocationFormDialog(this.locationService, this.userGroupService, location, this::loadData).open();
    }

    @Override
    protected void delete(LocationEntity location) throws EntityInUseException {
        this.locationService.delete(location);
    }

    @Override
    protected String deleteDialogTitle() {
        return "Standort löschen";
    }

    @Override
    protected String deleteDialogQuestion(LocationEntity location) {
        return "Möchten Sie diesen Standort wirklich löschen? " + location.getName();
    }
}
