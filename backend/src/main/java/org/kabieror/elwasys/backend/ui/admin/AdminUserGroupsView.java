package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.events.UserGroupChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.UserGroupFormDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Benutzergruppenverwaltung (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../views/UserGroupsView} (Alt-Portal, Testfälle P9/P13).
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * eine Benutzergruppe anlegt, bearbeitet oder löscht. Gerüst und Lösch-Ablauf kommen aus
 * {@link AbstractAdminListView} (Issue #92).
 */
@Route(value = "admin/user-groups", layout = AdminLayout.class)
@PageTitle("Benutzergruppen - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUserGroupsView extends AbstractAdminListView<UserGroupEntity> {

    private final UserGroupService userGroupService;
    private final LocationService locationService;
    private final DeviceService deviceService;
    private final ProgramService programService;

    public AdminUserGroupsView(UserGroupService userGroupService, LocationService locationService,
            DeviceService deviceService, ProgramService programService, UiBroadcaster broadcaster) {
        super("Benutzergruppen", "admin-user-groups-view", "Neu", broadcaster);
        this.userGroupService = userGroupService;
        this.locationService = locationService;
        this.deviceService = deviceService;
        this.programService = programService;
        initGrid();
    }

    @Override
    protected void configureColumns(Grid<UserGroupEntity> grid) {
        grid.addColumn(UserGroupEntity::getName).setHeader("Name").setSortable(true);
        grid.addColumn(this::formatDiscount).setHeader("Rabatt");
    }

    private String formatDiscount(UserGroupEntity group) {
        // Rabattwert ist ein primitives double (nicht BigDecimal wie die Geldbeträge im Portal) -
        // deshalb hier bewusst direkt NumberFormat statt PortalFormats.currency, sowohl für den
        // Betrags- als auch für den Prozentfall (eine Prozent-Anzeige gibt es ohnehin nur hier).
        if (group.getDiscountType() == DiscountType.FIX) {
            return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(group.getDiscountValue());
        } else if (group.getDiscountType() == DiscountType.FACTOR) {
            return NumberFormat.getPercentInstance(Locale.GERMANY).format(group.getDiscountValue());
        }
        return "-";
    }

    @Override
    protected List<UserGroupEntity> findAll() {
        return this.userGroupService.findAll();
    }

    /** Suchtext für das Filterfeld (UI-Redesign v2 AP4): Name und formatierter Rabatt. */
    @Override
    protected String filterableText(UserGroupEntity group) {
        return group.getName() + " " + formatDiscount(group);
    }

    @Override
    protected boolean isRelevantChange(Object event) {
        return event instanceof UserGroupChangedEvent;
    }

    @Override
    protected void openCreateDialog() {
        new UserGroupFormDialog(this.userGroupService, this.locationService, this.deviceService, this.programService,
                null, this::loadData).open();
    }

    @Override
    protected void openEditDialog(UserGroupEntity group) {
        new UserGroupFormDialog(this.userGroupService, this.locationService, this.deviceService, this.programService,
                group, this::loadData).open();
    }

    @Override
    protected void delete(UserGroupEntity group) throws EntityInUseException {
        this.userGroupService.delete(group);
    }

    @Override
    protected String deleteDialogTitle() {
        return "Benutzergruppe löschen";
    }

    @Override
    protected String deleteDialogQuestion(UserGroupEntity group) {
        return "Möchten Sie diese Benutzergruppe wirklich löschen? " + group.getName()
                + " Benutzern, denen die Gruppe derzeit zugewiesen ist, wird eine andere Gruppe zugewiesen.";
    }
}
