package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.PasswordService;
import org.kabieror.elwasys.backend.service.UserService;

/**
 * Kopfzeilen-Komponente für {@link org.kabieror.elwasys.backend.ui.admin.AdminLayout} und
 * {@link org.kabieror.elwasys.backend.ui.user.UserLayout} (Phase 3 AP1, erweitert AP4, siehe
 * kb/05-migration-plan.md): zeigt den Namen des angemeldeten Benutzers als aufklappbares Menü
 * mit "Einstellungen"/"Passwort ändern"/"Logout" - fachlicher Nachfolger des Benutzermenüs in
 * {@code Portal/.../components/MainMenu} (Alt-Portal), dessen drei Menüpunkte hier 1:1
 * übernommen sind (AP1 hatte hier bewusst nur Logout, siehe Änderungslog "Phase 3 AP1"/"AP4").
 */
public class UserMenuBar extends HorizontalLayout {

    public UserMenuBar(AuthenticationContext authenticationContext, UserService userService,
            PasswordService passwordService) {
        addClassName("user-menu-bar");
        setSpacing(true);

        String displayName = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getName)
                .or(authenticationContext::getPrincipalName)
                .orElse("");

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        var rootItem = menuBar.addItem(displayName);
        var subMenu = rootItem.getSubMenu();

        subMenu.addItem("Einstellungen", e -> currentUser(authenticationContext, userService).ifPresent(
                user -> new UserSettingsDialog(userService, user, () -> {
                }).open()));

        subMenu.addItem("Passwort ändern", e -> currentUser(authenticationContext, userService).ifPresent(
                user -> new ChangePasswordDialog(passwordService, user, () -> {
                }).open()));

        subMenu.addItem("Logout", e -> authenticationContext.logout());

        add(menuBar);
    }

    private static java.util.Optional<UserEntity> currentUser(AuthenticationContext authenticationContext,
            UserService userService) {
        return authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getUserId).flatMap(userService::findById);
    }
}
