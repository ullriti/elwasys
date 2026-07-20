package org.kabieror.elwasys.backend.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.kabieror.elwasys.backend.ui.admin.AdminDashboardView;
import org.kabieror.elwasys.backend.ui.user.UserDashboardView;

/**
 * Einstiegs-Route ("/") nach dem Login (Phase 3 AP1, siehe kb/05-migration-plan.md) - leitet
 * je nach Rolle sofort weiter: Administratoren ins Admin-Dashboard, alle anderen angemeldeten
 * Benutzer ins Benutzer-Dashboard. Fachlicher Nachfolger von
 * {@code Portal/.../WaschportalUI#loadSessionContent}, das dort abhängig vom
 * {@code SessionManager.AuthorizedType} zwischen {@code AdministratorLayout} und
 * {@code UserLayout} wählte (siehe kb/05-migration-plan.md, Testfälle P2/P15).
 *
 * <p>{@code @PermitAll} statt {@code @AnonymousAllowed}: diese Route selbst verlangt bereits
 * eine Anmeldung (nicht-authentifizierte Zugriffe werden von der HTTP-Sicherheitskette in
 * {@link org.kabieror.elwasys.backend.auth.SecurityConfig} vor Erreichen dieser View schon zur
 * Login-Seite umgeleitet) - die Weiterleitung selbst ist reine Navigationslogik ohne eigenen
 * Inhalt.
 */
@Route("")
@PermitAll
public class RootView extends Div implements BeforeEnterObserver {

    private final AuthenticationContext authenticationContext;

    public RootView(AuthenticationContext authenticationContext) {
        this.authenticationContext = authenticationContext;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (this.authenticationContext.hasRole("ADMIN")) {
            event.forwardTo(AdminDashboardView.class);
        } else {
            event.forwardTo(UserDashboardView.class);
        }
    }
}
