package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Benutzergruppenverwaltung (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../views/UserGroupsView} (Alt-Portal, siehe
 * Testfall P9). Inhalte folgen in AP2/AP3.
 */
@Route(value = "admin/user-groups", layout = AdminLayout.class)
@PageTitle("Benutzergruppen - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUserGroupsView extends PlaceholderView {

    public AdminUserGroupsView() {
        super("Benutzergruppen", "Die Benutzergruppenverwaltung folgt in einem späteren Arbeitspaket.");
    }
}
