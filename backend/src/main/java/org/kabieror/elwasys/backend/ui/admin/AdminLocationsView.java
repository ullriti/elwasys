package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.LocationFormDialog;
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
 */
@Route(value = "admin/locations", layout = AdminLayout.class)
@PageTitle("Standorte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminLocationsView extends AbstractAdminListView<LocationEntity> {

    private final LocationService locationService;
    private final UserGroupService userGroupService;

    public AdminLocationsView(LocationService locationService, UserGroupService userGroupService,
            UiBroadcaster broadcaster) {
        super("Standorte", "admin-locations-view", "Neu", broadcaster);
        this.locationService = locationService;
        this.userGroupService = userGroupService;
        initGrid();
    }

    @Override
    protected void configureColumns(Grid<LocationEntity> grid) {
        grid.addColumn(LocationEntity::getName).setHeader("Name").setSortable(true);
        grid.addColumn(l -> l.getValidUserGroups().size()).setHeader("Benutzergruppen");
        grid.addColumn(l -> l.getOfflineMaxDurationMinutes() + " min").setHeader("Offline-Maximaldauer");
    }

    @Override
    protected List<LocationEntity> findAll() {
        return this.locationService.findAll();
    }

    @Override
    protected boolean isRelevantChange(Object event) {
        return event instanceof LocationChangedEvent;
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
