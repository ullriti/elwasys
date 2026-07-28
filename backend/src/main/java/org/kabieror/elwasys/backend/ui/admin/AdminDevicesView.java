package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.events.DeviceChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.DeviceFormDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Geräteverwaltung (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../views/DevicesView} (Alt-Portal, Testfälle P10/P11).
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * ein Gerät anlegt, bearbeitet oder löscht. Gerüst und Lösch-Ablauf kommen aus
 * {@link AbstractAdminListView} (Issue #92); der Lösch-Wächter für ein belegtes Gerät (#38)
 * bleibt davon unberührt - er wirft weiterhin {@link EntityInUseException} im Service.
 */
@Route(value = "admin/devices", layout = AdminLayout.class)
@PageTitle("Geräte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDevicesView extends AbstractAdminListView<DeviceEntity> {

    private final DeviceService deviceService;
    private final LocationService locationService;
    private final ProgramService programService;
    private final UserGroupService userGroupService;

    public AdminDevicesView(DeviceService deviceService, LocationService locationService,
            ProgramService programService, UserGroupService userGroupService, UiBroadcaster broadcaster) {
        super("Geräte", "admin-devices-view", "Neu", broadcaster);
        this.deviceService = deviceService;
        this.locationService = locationService;
        this.programService = programService;
        this.userGroupService = userGroupService;
        initGrid();
    }

    @Override
    protected void configureColumns(Grid<DeviceEntity> grid) {
        grid.addColumn(DeviceEntity::getPosition).setHeader("Position").setSortable(true).setFlexGrow(0)
                .setWidth("90px");
        grid.addColumn(DeviceEntity::getName).setHeader("Name").setSortable(true);
        grid.addColumn(d -> d.getLocation().getName()).setHeader("Standort").setSortable(true);
        grid.addComponentColumn(this::statusBadge).setHeader("Status");
    }

    /**
     * Beschriftung der Status-Spalte. Eigene Methode, damit Badge-Text und Suchtext
     * ({@link #filterableText}) dieselbe Quelle haben - sonst fände der Filter eine später
     * geänderte Beschriftung nicht mehr, ohne dass es auffiele.
     */
    private static String statusLabel(DeviceEntity device) {
        return device.isEnabled() ? "Aktiviert" : "Deaktiviert";
    }

    private Span statusBadge(DeviceEntity device) {
        Span badge = new Span(statusLabel(device));
        badge.getElement().getThemeList().add("badge" + (device.isEnabled() ? " success" : " contrast"));
        return badge;
    }

    @Override
    protected List<DeviceEntity> findAll() {
        return this.deviceService.findAll();
    }

    /** Suchtext für das Filterfeld (UI-Redesign v2 AP4): Position, Name, Standort und Status. */
    @Override
    protected String filterableText(DeviceEntity device) {
        return device.getPosition() + " " + device.getName() + " " + device.getLocation().getName() + " "
                + statusLabel(device);
    }

    @Override
    protected boolean isRelevantChange(Object event) {
        return event instanceof DeviceChangedEvent;
    }

    @Override
    protected void openCreateDialog() {
        new DeviceFormDialog(this.deviceService, this.locationService, this.programService, this.userGroupService,
                null, this::loadData).open();
    }

    @Override
    protected void openEditDialog(DeviceEntity device) {
        new DeviceFormDialog(this.deviceService, this.locationService, this.programService, this.userGroupService,
                device, this::loadData).open();
    }

    @Override
    protected void delete(DeviceEntity device) throws EntityInUseException {
        this.deviceService.delete(device);
    }

    @Override
    protected String deleteDialogTitle() {
        return "Gerät löschen";
    }

    @Override
    protected String deleteDialogQuestion(DeviceEntity device) {
        return "Möchten Sie dieses Gerät wirklich löschen? " + device.getName();
    }
}
