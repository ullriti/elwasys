package org.kabieror.elwasys.backend.ui.user;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.kabieror.elwasys.backend.service.PasswordService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.component.UserMenuBar;

/**
 * Rahmen-Layout für den Selbstbedienungsbereich normaler Benutzer (Phase 3 AP1, siehe
 * docs/kb/05-migration-plan.md) - fachlicher Nachfolger von {@code Portal/.../UserLayout}
 * (Alt-Portal). Laut Auftraggeber (docs/kb/05-migration-plan.md, "Entscheidungen",
 * Nutzungsprofil Portal) loggen sich im Wesentlichen nur Admins ein; dieser Bereich bleibt
 * funktional, hat aber niedrigere Parity-Priorität - entsprechend schlank (ein Menüpunkt,
 * wie im Alt-Portal).
 */
public class UserLayout extends AppLayout {

    public UserLayout(AuthenticationContext authenticationContext, UserService userService,
            PasswordService passwordService) {
        DrawerToggle toggle = new DrawerToggle();

        H1 title = new H1("Waschportal");
        title.addClassName("user-layout-title");

        addToNavbar(toggle, title, new UserMenuBar(authenticationContext, userService, passwordService));

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Übersicht", UserDashboardView.class, VaadinIcon.HOME.create()));
        addToDrawer(nav);
    }
}
