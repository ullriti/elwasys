package org.kabieror.elwasys.backend.ui.admin;

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
 * Rahmen-Layout für den Admin-Bereich (Phase 3 AP1, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../AdministratorLayout} + {@code components/MainMenu}
 * (Alt-Portal). Die Navigationspunkte entsprechen 1:1 dem alten Hauptmenü (Dashboard,
 * Benutzer, Benutzergruppen, Programme, Geräte); "Standorte" ist als eigener Menüpunkt NEU
 * (im Alt-Portal nur über einen Dialog auf dem Dashboard erreichbar, siehe
 * {@code Portal/.../components/LocationWindow}) - eine bewusste, laut Auftraggeber
 * ausdrücklich erwünschte UX-Verbesserung (siehe docs/kb/05-migration-plan.md, "Entscheidungen",
 * Gestaltungsrahmen Portal-Neubau), keine Funktionsänderung. Inhalte der referenzierten
 * Views sind in diesem Arbeitspaket Platzhalter (siehe docs/kb/05-migration-plan.md
 * Phase-3-Roadmap: AP2/AP3 füllen sie mit den eigentlichen Stammdaten-/CRUD-Ansichten).
 *
 * <p>Zugriff ist auf {@code ROLE_ADMIN} beschränkt - jede Ziel-View trägt zusätzlich selbst
 * {@code @RolesAllowed("ADMIN")} (Vaadins {@code NavigationAccessControl} prüft pro Route, ein
 * Layout allein schützt keine Kind-Routen), damit ein Nicht-Administrator eine Admin-Route
 * auch bei direkter URL-Eingabe nicht erreichen kann (vgl. Testfall P18).
 */
public class AdminLayout extends AppLayout {

    public AdminLayout(AuthenticationContext authenticationContext, UserService userService,
            PasswordService passwordService) {
        DrawerToggle toggle = new DrawerToggle();

        H1 title = new H1("Waschportal");
        title.addClassName("admin-layout-title");

        addToNavbar(toggle, title, new UserMenuBar(authenticationContext, userService, passwordService));

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Dashboard", AdminDashboardView.class, VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Benutzer", AdminUsersView.class, VaadinIcon.USER.create()));
        nav.addItem(new SideNavItem("Benutzergruppen", AdminUserGroupsView.class, VaadinIcon.USERS.create()));
        nav.addItem(new SideNavItem("Programme", AdminProgramsView.class, VaadinIcon.COG.create()));
        nav.addItem(new SideNavItem("Geräte", AdminDevicesView.class, VaadinIcon.CUBES.create()));
        nav.addItem(new SideNavItem("Standorte", AdminLocationsView.class, VaadinIcon.MAP_MARKER.create()));

        addToDrawer(nav);
    }
}
