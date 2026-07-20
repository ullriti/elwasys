package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Programmverwaltung (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../views/ProgramsView} (Alt-Portal, siehe Testfall P12).
 * Inhalte folgen in AP2/AP3.
 */
@Route(value = "admin/programs", layout = AdminLayout.class)
@PageTitle("Programme - Waschportal")
@RolesAllowed("ADMIN")
public class AdminProgramsView extends PlaceholderView {

    public AdminProgramsView() {
        super("Programme", "Die Programmverwaltung folgt in einem späteren Arbeitspaket.");
    }
}
