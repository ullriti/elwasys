package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Geräteverwaltung (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../views/DevicesView} (Alt-Portal, siehe Testfälle P10/P11).
 * Inhalte folgen in AP2/AP3.
 */
@Route(value = "admin/devices", layout = AdminLayout.class)
@PageTitle("Geräte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDevicesView extends PlaceholderView {

    public AdminDevicesView() {
        super("Geräte", "Die Geräteverwaltung folgt in einem späteren Arbeitspaket.");
    }
}
