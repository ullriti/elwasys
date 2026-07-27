package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import java.util.Optional;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.PasswordService;
import org.kabieror.elwasys.backend.service.UserService;

/**
 * Kopfzeilen-Komponente für {@link org.kabieror.elwasys.backend.ui.admin.AdminLayout} und
 * {@link org.kabieror.elwasys.backend.ui.user.UserLayout} (Phase 3 AP1, erweitert AP4, siehe
 * docs/kb/05-migration-plan.md): zeigt den Namen des angemeldeten Benutzers als aufklappbares Menü
 * mit "Einstellungen"/"Passwort ändern"/"Logout" - fachlicher Nachfolger des Benutzermenüs in
 * {@code Portal/.../components/MainMenu} (Alt-Portal), dessen drei Menüpunkte hier 1:1
 * übernommen sind (AP1 hatte hier bewusst nur Logout, siehe Änderungslog "Phase 3 AP1"/"AP4").
 *
 * <p><b>UI-Redesign v2 (siehe docs/specs/0002-ui-design/v2/MAPPING.md, "Rahmen")</b>: aus dem
 * reinen Namen wird ein Chip aus Initialen-{@link Avatar} und Name, das Untermenü bekommt einen
 * nicht klickbaren Kopfblock (Avatar, Name, Rolle), Trennlinien und Symbole; "Logout" ist über
 * die Lumo-Theme-Variante {@code error} rot abgesetzt. Die drei <b>Beschriftungen bleiben
 * wortgleich</b> - die E2E-Suite klickt sie über {@code getByRole('menuitem', {name})} an
 * (backend/e2e/tests/helpers.ts#openUserMenu), und der Kopfblock ist deshalb bewusst KEIN
 * Menüpunkt, sondern eine über {@code SubMenu#add} eingehängte Layout-Komponente.
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
        // Rolle nur als Beschriftung im Menükopf - die eigentliche Autorisierung läuft
        // unverändert über Spring Security bzw. Vaadins NavigationAccessControl.
        boolean admin = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::isAdmin)
                .orElse(false);
        String roleLabel = admin ? "Administrator" : "Benutzer";

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        MenuItem rootItem = menuBar.addItem(buildChip(displayName));
        SubMenu subMenu = rootItem.getSubMenu();

        subMenu.add(buildHeader(displayName, roleLabel), new Hr());

        MenuItem settings = subMenu.addItem("Einstellungen",
                e -> currentUser(authenticationContext, userService).ifPresent(
                        user -> new UserSettingsDialog(userService, user, () -> {
                        }).open()));
        settings.addComponentAsFirst(menuIcon(VaadinIcon.COG));

        MenuItem changePassword = subMenu.addItem("Passwort ändern",
                e -> currentUser(authenticationContext, userService).ifPresent(
                        user -> new ChangePasswordDialog(passwordService, user, () -> {
                        }).open()));
        changePassword.addComponentAsFirst(menuIcon(VaadinIcon.KEY));

        subMenu.add(new Hr());

        MenuItem logout = subMenu.addItem("Logout", e -> authenticationContext.logout());
        logout.addComponentAsFirst(menuIcon(VaadinIcon.SIGN_OUT));
        logout.getElement().getThemeList().add("error");

        add(menuBar);
    }

    /** Benutzer-Chip im Kopfbalken: Initialen-Avatar links, Anzeigename rechts. */
    private static HorizontalLayout buildChip(String displayName) {
        Span name = new Span(displayName);
        name.addClassName("user-menu-name");

        HorizontalLayout chip = new HorizontalLayout(buildAvatar(displayName), name);
        chip.addClassName("user-menu-chip");
        chip.setSpacing(false);
        chip.setPadding(false);
        chip.setAlignItems(Alignment.CENTER);
        return chip;
    }

    /**
     * Kopfblock des aufgeklappten Menüs. Bewusst KEIN {@link MenuItem}: er ist nicht anklickbar
     * und darf die namensbasierte Menüpunkt-Suche der E2E-Suite nicht stören (siehe
     * Klassen-Javadoc).
     */
    private static HorizontalLayout buildHeader(String displayName, String roleLabel) {
        Span name = new Span(displayName);
        name.addClassName("user-menu-name");
        Span role = new Span(roleLabel);
        role.addClassName("user-menu-role");

        VerticalLayout texts = new VerticalLayout(name, role);
        texts.setSpacing(false);
        texts.setPadding(false);

        HorizontalLayout header = new HorizontalLayout(buildAvatar(displayName), texts);
        header.addClassName("user-menu-header");
        header.setSpacing(false);
        header.setPadding(false);
        header.setAlignItems(Alignment.CENTER);
        return header;
    }

    private static Avatar buildAvatar(String displayName) {
        Avatar avatar = new Avatar();
        avatar.setName(displayName);
        avatar.setAbbreviation(initials(displayName));
        // Der Name steht direkt daneben - ein zusätzlicher Tooltip auf dem Avatar wäre nur
        // Rauschen.
        avatar.setTooltipEnabled(false);
        return avatar;
    }

    /**
     * Initialen des Anzeigenamens (höchstens zwei Buchstaben). Ohne diese Vorgabe leitet
     * {@code vaadin-avatar} die Abkürzung zwar selbst aus dem Namen ab, aber erst clientseitig
     * und mit eigener Heuristik - hier soll sichtbar dieselbe Regel gelten wie im Prototyp.
     */
    private static String initials(String displayName) {
        StringBuilder initials = new StringBuilder(2);
        for (String part : displayName.trim().split("\\s+")) {
            if (!part.isEmpty() && initials.length() < 2) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
        }
        return initials.toString();
    }

    /**
     * Symbol eines Menüpunkts. Es trägt keinen eigenen Text und ändert deshalb den
     * barrierefreien Namen des Menüpunkts nicht - die E2E-Suite findet ihn weiterhin über die
     * unveränderte Beschriftung.
     */
    private static Icon menuIcon(VaadinIcon icon) {
        Icon result = icon.create();
        result.setSize("var(--lumo-icon-size-s)");
        return result;
    }

    private static Optional<UserEntity> currentUser(AuthenticationContext authenticationContext,
            UserService userService) {
        return authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getUserId).flatMap(userService::findById);
    }
}
