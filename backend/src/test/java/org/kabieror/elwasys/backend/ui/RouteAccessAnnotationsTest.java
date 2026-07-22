package org.kabieror.elwasys.backend.ui;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.ui.admin.AdminDashboardView;
import org.kabieror.elwasys.backend.ui.admin.AdminDevicesView;
import org.kabieror.elwasys.backend.ui.admin.AdminLocationsView;
import org.kabieror.elwasys.backend.ui.admin.AdminProgramsView;
import org.kabieror.elwasys.backend.ui.admin.AdminUserGroupsView;
import org.kabieror.elwasys.backend.ui.admin.AdminUsersView;
import org.kabieror.elwasys.backend.ui.login.LoginView;
import org.kabieror.elwasys.backend.ui.login.ResetPasswordView;
import org.kabieror.elwasys.backend.ui.user.UserDashboardView;

/**
 * Reiner Reflection-Test (kein Spring-Kontext, keine DB) für den rollenbasierten
 * Routen-Schutz des Vaadin-Grundgerüsts (Phase 3 AP1, siehe docs/kb/05-migration-plan.md, vgl.
 * Testfall P18: Nicht-Admins sehen/erreichen keine Admin-Views). Vaadins
 * {@code NavigationAccessControl} (aktiviert über {@code VaadinSecurityConfigurer} in
 * {@link org.kabieror.elwasys.backend.auth.SecurityConfig}) wertet genau diese
 * JSR-250-Annotationen pro View-Klasse aus, um bei jeder Navigation zu entscheiden, ob der
 * angemeldete Benutzer die Route betreten darf - dieser Test sichert die Zuordnung
 * View-Klasse zu Berechtigung gegen versehentliche Änderungen ab (z.B. eine neue Admin-View,
 * die die Annotation vergisst und damit standardmäßig für JEDEN abgelehnt würde, oder
 * schlimmer, versehentlich mit {@code @AnonymousAllowed} versehen wird).
 *
 * <p>Ergänzt {@link VaadinPortalSecurityTest} (Sicherheits-Filterebene, MOCK). Ein Test über
 * einen echten eingebetteten Servlet-Container (RANDOM_PORT) wurde bewusst NICHT ergänzt: in
 * dieser Umgebung fehlt der Netzwerkzugriff auf vaadin.com, den Vaadins Lizenzprüfung im
 * Dev-Modus beim ersten {@code VaadinServlet#init()} verlangt (siehe
 * docs/kb/05-migration-plan.md, "Offene Punkte/Risiken Phase 3 AP1", sowie die Begründung der
 * Abhängigkeits-Ausschlüsse in backend/pom.xml) - ein solcher Test könnte hier nicht grün
 * laufen, unabhängig vom Java-Code dieses Arbeitspakets. Ein vollständiger, per Browser/JS
 * getriebener Login-Durchstich (Vaadins {@code LoginForm} ist ohnehin eine clientseitig
 * gerenderte Web-Komponente, kein klassisches Server-HTML-Formular mit scrapebarem CSRF-Feld)
 * bleibt der späteren Playwright-E2E-Suite vorbehalten (siehe docs/kb/08-test-plan.md, P18).
 */
class RouteAccessAnnotationsTest {

    @Test
    void loginViewAllowsAnonymousAccess() {
        assertThat(LoginView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class))
                .as("LoginView muss ohne Anmeldung erreichbar sein").isTrue();
    }

    @Test
    void resetPasswordViewAllowsAnonymousAccess() {
        // Phase 3 AP4 (siehe docs/kb/05-migration-plan.md, Testfall P19): die öffentliche
        // Passwort-Reset-Ansicht muss - wie LoginView - ohne Anmeldung erreichbar sein, sonst
        // könnte ein Nutzer den per Email verschickten Link nie öffnen.
        assertThat(ResetPasswordView.class.isAnnotationPresent(com.vaadin.flow.server.auth.AnonymousAllowed.class))
                .as("ResetPasswordView muss ohne Anmeldung erreichbar sein").isTrue();
    }

    @Test
    void rootViewRequiresAnyAuthenticatedUser() {
        assertThat(RootView.class.isAnnotationPresent(PermitAll.class))
                .as("RootView (Rollen-Weiterleitung nach Login) verlangt nur eine Anmeldung, keine bestimmte Rolle")
                .isTrue();
    }

    @Test
    void allAdminViewsRequireTheAdminRole() {
        for (Class<?> adminView : new Class<?>[] {AdminDashboardView.class, AdminUsersView.class,
                AdminUserGroupsView.class, AdminProgramsView.class, AdminDevicesView.class,
                AdminLocationsView.class}) {
            RolesAllowed rolesAllowed = adminView.getAnnotation(RolesAllowed.class);
            assertThat(rolesAllowed).as("%s muss @RolesAllowed tragen", adminView.getSimpleName()).isNotNull();
            assertThat(rolesAllowed.value()).as("%s muss auf die Admin-Rolle beschränkt sein",
                    adminView.getSimpleName()).containsExactly("ADMIN");
        }
    }

    @Test
    void userDashboardRequiresTheUserRole() {
        RolesAllowed rolesAllowed = UserDashboardView.class.getAnnotation(RolesAllowed.class);
        assertThat(rolesAllowed).isNotNull();
        assertThat(rolesAllowed.value()).containsExactly("USER");
    }
}
