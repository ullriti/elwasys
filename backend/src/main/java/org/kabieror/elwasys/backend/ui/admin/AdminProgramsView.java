package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.events.ProgramChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.ProgramFormDialog;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Programmverwaltung (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../views/ProgramsView} (Alt-Portal, Testfall P12) inkl. des
 * Lösch-Wächters ("Programm ist noch auf N Gerät(en) verfügbar").
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * ein Programm anlegt, bearbeitet oder löscht. Gerüst und Lösch-Ablauf kommen aus
 * {@link AbstractAdminListView} (Issue #92).
 */
@Route(value = "admin/programs", layout = AdminLayout.class)
@PageTitle("Programme - Waschportal")
@RolesAllowed("ADMIN")
public class AdminProgramsView extends AbstractAdminListView<ProgramEntity> {

    private final ProgramService programService;
    private final UserGroupService userGroupService;

    public AdminProgramsView(ProgramService programService, UserGroupService userGroupService,
            UiBroadcaster broadcaster) {
        super("Programme", "admin-programs-view", "Neu", broadcaster);
        this.programService = programService;
        this.userGroupService = userGroupService;
        initGrid();
    }

    @Override
    protected void configureColumns(Grid<ProgramEntity> grid) {
        grid.addColumn(ProgramEntity::getName).setHeader("Name").setSortable(true);
        grid.addColumn(AdminProgramsView::typeLabel).setHeader("Typ").setSortable(true);
        grid.addColumn(this::formatPrice).setHeader("Preis");
    }

    /**
     * Beschriftung der Typ-Spalte. Eigene Methode, damit Spaltentext und Suchtext
     * ({@link #filterableText}) dieselbe Quelle haben - sonst fände der Filter eine später
     * geänderte Beschriftung nicht mehr, ohne dass es auffiele (wie bei {@link #formatPrice}).
     */
    private static String typeLabel(ProgramEntity program) {
        return program.getType() == ProgramType.DYNAMIC ? "Dynamisch" : "Statisch";
    }

    private String formatPrice(ProgramEntity program) {
        if (program.getType() == ProgramType.DYNAMIC) {
            String unit = switch (program.getTimeUnit()) {
                case HOURS -> "h";
                case MINUTES -> "min";
                case SECONDS -> "s";
                case null -> "?";
            };
            return PortalFormats.currency(program.getFlagfall()) + " + " + PortalFormats.currency(
                    program.getRate()) + " / " + unit;
        }
        return PortalFormats.currency(program.getFlagfall());
    }

    @Override
    protected List<ProgramEntity> findAll() {
        return this.programService.findAll();
    }

    /** Suchtext für das Filterfeld (UI-Redesign v2 AP4): Name, Typ und formatierter Preis. */
    @Override
    protected String filterableText(ProgramEntity program) {
        return program.getName() + " " + typeLabel(program) + " " + formatPrice(program);
    }

    @Override
    protected boolean isRelevantChange(Object event) {
        return event instanceof ProgramChangedEvent;
    }

    @Override
    protected void openCreateDialog() {
        new ProgramFormDialog(this.programService, this.userGroupService, null, this::loadData).open();
    }

    @Override
    protected void openEditDialog(ProgramEntity program) {
        new ProgramFormDialog(this.programService, this.userGroupService, program, this::loadData).open();
    }

    @Override
    protected void delete(ProgramEntity program) throws EntityInUseException {
        this.programService.delete(program);
    }

    @Override
    protected String deleteDialogTitle() {
        return "Programm löschen";
    }

    @Override
    protected String deleteDialogQuestion(ProgramEntity program) {
        return "Möchten Sie dieses Programm wirklich löschen? " + program.getName();
    }
}
