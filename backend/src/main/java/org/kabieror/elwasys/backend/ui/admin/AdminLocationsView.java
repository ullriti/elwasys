package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Standortverwaltung (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) - im Alt-Portal
 * kein eigener Menüpunkt (dort nur über einen Dialog vom Dashboard erreichbar, siehe
 * {@code Portal/.../components/LocationWindow}, Testfall P14); als eigener Navigationspunkt
 * eine bewusste UX-Verbesserung (siehe kb/05-migration-plan.md, "Entscheidungen",
 * Gestaltungsrahmen Portal-Neubau: Struktur bleibt wiedererkennbar, UX-Verbesserungen sind
 * erwünscht). Inhalte folgen in AP2/AP3.
 */
@Route(value = "admin/locations", layout = AdminLayout.class)
@PageTitle("Standorte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminLocationsView extends PlaceholderView {

    public AdminLocationsView() {
        super("Standorte", "Die Standortverwaltung folgt in einem späteren Arbeitspaket.");
    }
}
