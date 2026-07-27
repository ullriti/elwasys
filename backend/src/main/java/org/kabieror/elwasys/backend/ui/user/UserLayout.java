package org.kabieror.elwasys.backend.ui.user;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.kabieror.elwasys.backend.service.PasswordService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.component.NavbarViewName;
import org.kabieror.elwasys.backend.ui.component.UserMenuBar;

/**
 * Rahmen-Layout für den Selbstbedienungsbereich normaler Benutzer (Phase 3 AP1, siehe
 * docs/kb/05-migration-plan.md) - fachlicher Nachfolger von {@code Portal/.../UserLayout}
 * (Alt-Portal). Laut Auftraggeber (docs/kb/05-migration-plan.md, "Entscheidungen",
 * Nutzungsprofil Portal) loggen sich im Wesentlichen nur Admins ein; dieser Bereich bleibt
 * funktional, hat aber niedrigere Parity-Priorität - entsprechend schlank (ein Menüpunkt,
 * wie im Alt-Portal).
 *
 * <p><b>UI-Redesign v2 (siehe docs/specs/0002-ui-design/v2/MAPPING.md, "Rahmen")</b>: der
 * Kopfbalken bekommt - wie im Admin-Bereich - einen dekorativen Logo-Mark und über
 * {@link NavbarViewName} den Namen der aktuellen Ansicht, die Seitenleiste steht dauerhaft
 * offen. Der einzige Menüpunkt bleibt unverändert.
 */
public class UserLayout extends AppLayout {

    public UserLayout(AuthenticationContext authenticationContext, UserService userService,
            PasswordService passwordService) {
        DrawerToggle toggle = new DrawerToggle();

        // Reine Deko (abgerundetes Quadrat mit Kreis, komplett in portal-theme.css gezeichnet) -
        // trägt keine Information und wird deshalb vor Screenreadern verborgen.
        Div logoMark = new Div();
        logoMark.addClassName("navbar-logo-mark");
        logoMark.getElement().setAttribute("aria-hidden", "true");

        H1 title = new H1("Waschportal");
        title.addClassName("user-layout-title");

        addToNavbar(toggle, logoMark, title, new NavbarViewName(),
                new UserMenuBar(authenticationContext, userService, passwordService));

        // Auf dem Desktop bleibt die Seitenleiste sichtbar; unterhalb der CSS-Schwelle von
        // 900px führt die AppLayout sie weiterhin als Overlay über den DrawerToggle.
        setDrawerOpened(true);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Übersicht", UserDashboardView.class, VaadinIcon.HOME.create()));
        addToDrawer(nav);
    }
}
