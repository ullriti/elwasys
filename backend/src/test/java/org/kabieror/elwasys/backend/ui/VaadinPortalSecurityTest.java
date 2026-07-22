package org.kabieror.elwasys.backend.ui;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.kabieror.elwasys.backend.support.TestPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sicherheits-Filterebene des Vaadin-Flow-Grundgerüsts (Phase 3 AP1, siehe
 * docs/kb/05-migration-plan.md): geschützte Routen leiten nicht angemeldete Anfragen auf die
 * Login-Seite um, und die Anbindung an
 * {@link org.kabieror.elwasys.backend.auth.ElwasysAuthenticationProvider} funktioniert über
 * genau die Login-Route, die {@link org.kabieror.elwasys.backend.auth.SecurityConfig} bei
 * {@code VaadinSecurityConfigurer} registriert (siehe deren Javadoc) - inkl. der bewussten
 * Verschärfung, dass gesperrte Benutzer abgewiesen werden. Ergänzt
 * {@link org.kabieror.elwasys.backend.auth.SecurityConfigTest} (AP3), die den generischen
 * Formular-Login bereits vor der Vaadin-Anbindung abgedeckt hat.
 *
 * <p>Diese Tests laufen bewusst über {@code webEnvironment = MOCK} (kein echter eingebetteter
 * Servlet-Container): das genügt für alles, was rein in der Spring-Security-Filterkette
 * entschieden wird (Authentifizierung, Umleitung, Logout) - Vaadins EIGENE Servlet-/
 * Routing-Schicht (tatsächliches Rendern einer View) braucht dagegen einen echten Container
 * UND (siehe docs/kb/05-migration-plan.md, "Offene Punkte/Risiken Phase 3 AP1") in dieser Umgebung
 * einen Netzwerkzugriff auf vaadin.com, den es hier nicht gibt - ein entsprechender
 * RANDOM_PORT-Test wurde deshalb bewusst NICHT ergänzt (siehe kb-Eintrag für die Herleitung).
 * Der rollenbasierte Routen-Schutz (Admin- vs. Benutzer-Bereich, vgl. Testfall P18) wird
 * stattdessen über {@link RouteAccessAnnotationsTest} (reine Annotations-Prüfung ohne
 * Spring-Kontext/Servlet-Container) abgesichert. Eigene {@code @SpringBootTest}-Konfiguration
 * statt
 * {@link org.kabieror.elwasys.backend.support.AbstractBackendIT}: diese Klasse braucht einen
 * (Mock-)Servlet-Container ({@code webEnvironment = MOCK}), die Basisklasse ist bewusst
 * {@code WebEnvironment.NONE} (siehe deren Javadoc, seit AP1 zusätzlich mit
 * Vaadin-Autokonfigurations-Ausschluss).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class VaadinPortalSecurityTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordVerificationService passwordVerificationService;

    private UserEntity newUser(String username, String rawPassword, boolean admin, boolean blocked) {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Name"), username, group);
        user.setPassword(this.passwordVerificationService.encodeNew(rawPassword));
        user.setAdmin(admin);
        user.setBlocked(blocked);
        return this.userRepository.save(user);
    }

    @Test
    void adminRouteRedirectsUnauthenticatedUserToLogin() throws Exception {
        // Geschützte Route leitet nicht angemeldete Anfragen auf die Login-Seite um - diese
        // Entscheidung fällt bereits im Sicherheits-Filter (ExceptionTranslationFilter), bevor
        // die Anfrage überhaupt bis zum Vaadin-Servlet durchgereicht wird, und ist daher ohne
        // echten (eingebetteten) Servlet-Container über MockMvc prüfbar.
        this.mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }

    @Test
    void rootRouteRedirectsUnauthenticatedUserToLogin() throws Exception {
        this.mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());
    }

    @Test
    void formLoginThroughVaadinLoginRouteAuthenticatesValidUser() throws Exception {
        String username = Fixtures.unique("web-user");
        this.newUser(username, "s3cr3t!", false, false);

        this.mockMvc.perform(formLogin().user(username).password("s3cr3t!")).andExpect(authenticated());
    }

    @Test
    void formLoginThroughVaadinLoginRouteRejectsBlockedUser() throws Exception {
        // Bewusste Verschärfung ggü. Alt-Portal-Login (siehe ElwasysAuthenticationProvider-
        // Javadoc): gesperrte Nutzer werden abgewiesen, auch über die neue Vaadin-Login-Route.
        String username = Fixtures.unique("blocked-web-user");
        this.newUser(username, "s3cr3t!", false, true);

        this.mockMvc.perform(formLogin().user(username).password("s3cr3t!")).andExpect(unauthenticated());
    }

    @Test
    void logoutInvalidatesSessionAndRedirectsToLogin() throws Exception {
        this.mockMvc.perform(logout()).andExpect(status().is3xxRedirection());
    }
}
