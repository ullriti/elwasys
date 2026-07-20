package org.kabieror.elwasys.backend.ui.user;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.ui.component.PlaceholderView;

/**
 * Benutzer-Dashboard (Platzhalter, Phase 3 AP1, siehe kb/05-migration-plan.md) - Landeseite für
 * Nicht-Administratoren nach dem Login (siehe {@link org.kabieror.elwasys.backend.ui.RootView}).
 * Fachlicher Nachfolger von {@code Portal/.../views/UsersDashboardView} (Alt-Portal, zeigte dort
 * u.a. das eigene Guthaben, siehe Testfall P15: "Guthaben"/"Übersicht" sichtbar). Inhalte
 * folgen in AP3 (siehe kb/05-migration-plan.md Phase-3-Roadmap: Nutzer-Selbstbedienungsbereich
 * zuletzt, laut Auftraggeber niedrige Priorität).
 *
 * <p>{@code @RolesAllowed("USER")} statt {@code @PermitAll}: {@code ElwasysUserPrincipal}
 * vergibt {@code ROLE_USER} an ALLE Benutzer (auch Administratoren, siehe dessen Javadoc) -
 * die Einschränkung hier ist daher keine Zugriffslücke, sondern deckt sich mit
 * {@link org.kabieror.elwasys.backend.ui.RootView}, das Administratoren ohnehin ins
 * Admin-Dashboard weiterleitet, bevor diese View erreicht wird.
 */
@Route(value = "user", layout = UserLayout.class)
@PageTitle("Übersicht - Waschportal")
@RolesAllowed("USER")
public class UserDashboardView extends PlaceholderView {

    public UserDashboardView() {
        super("Übersicht", "Ihr Guthaben und Ihre Ausführungen folgen in einem späteren Arbeitspaket.");
    }
}
