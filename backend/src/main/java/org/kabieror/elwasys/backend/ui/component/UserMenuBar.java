package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;

/**
 * Kleine, wiederverwendbare Kopfzeilen-Komponente für {@link org.kabieror.elwasys.backend.ui.admin.AdminLayout}
 * und {@link org.kabieror.elwasys.backend.ui.user.UserLayout} (Phase 3 AP1, siehe
 * kb/05-migration-plan.md): zeigt den Namen des angemeldeten Benutzers und einen
 * Logout-Knopf - fachlicher Nachfolger des Benutzermenüs in
 * {@code Portal/.../components/MainMenu} (Alt-Portal), das dort zusätzlich
 * "Einstellungen"/"Passwort ändern" anbot. Diese beiden Funktionen sind bewusst NICHT Teil
 * dieses Arbeitspakets (Grundgerüst, siehe kb/05-migration-plan.md Phase-3-Roadmap -
 * "Dialoge/Funktionen" folgen in einem späteren Arbeitspaket).
 */
public class UserMenuBar extends HorizontalLayout {

    public UserMenuBar(AuthenticationContext authenticationContext) {
        addClassName("user-menu-bar");
        setSpacing(true);

        String displayName = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getName)
                .or(authenticationContext::getPrincipalName)
                .orElse("");

        Span userLabel = new Span(displayName);
        userLabel.addClassName("user-menu-bar-name");

        Button logout = new Button("Logout", e -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        add(userLabel, logout);
    }
}
