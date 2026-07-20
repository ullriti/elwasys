package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Benutzerverwaltung (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../views/UsersView} (Alt-Portal, siehe Testfälle P6-P8: Benutzer
 * anlegen/bearbeiten/sperren, Guthaben aufladen). Inhalte folgen in AP2/AP3.
 */
@Route(value = "admin/users", layout = AdminLayout.class)
@PageTitle("Benutzer - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUsersView extends PlaceholderView {

    public AdminUsersView() {
        super("Benutzer", "Die Benutzerverwaltung folgt in einem späteren Arbeitspaket.");
    }
}
