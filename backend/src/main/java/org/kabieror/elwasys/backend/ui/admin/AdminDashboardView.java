package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Admin-Dashboard (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) - Landeseite für
 * Administratoren nach dem Login (siehe {@link org.kabieror.elwasys.backend.ui.RootView}).
 * Fachlicher Nachfolger von {@code Portal/.../views/AdminDashboardView} (Alt-Portal, zeigte
 * dort u.a. Standort-/Gerätestatus-Kacheln, siehe Testfall P20) - Inhalte folgen in AP2/AP3.
 */
@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Dashboard - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDashboardView extends PlaceholderView {

    public AdminDashboardView() {
        super("Dashboard", "Der Standort-/Gerätestatus-Überblick folgt in einem späteren Arbeitspaket.");
    }
}
