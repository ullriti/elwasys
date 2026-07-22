package org.kabieror.elwasys.backend.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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
 * End-to-End-Beweis über die echte HTTP-/Servlet-Schicht (siehe docs/kb/05-migration-plan.md,
 * AP3, "Login-/Session-Handling"): {@link SecurityConfig} + {@link ElwasysAuthenticationProvider}
 * zusammen - Actuator-Health bleibt öffentlich erreichbar, alles andere verlangt per
 * Default eine Anmeldung, und ein Formular-Login mit gültigen Zugangsdaten erzeugt eine
 * authentifizierte Session.
 *
 * <p>Eigene {@code @SpringBootTest}-Konfiguration statt {@link
 * org.kabieror.elwasys.backend.support.AbstractBackendIT}: diese Klasse braucht einen
 * echten (Mock-)Servlet-Container ({@code webEnvironment = MOCK}), die Basisklasse ist
 * bewusst {@code WebEnvironment.NONE} (siehe deren Javadoc).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigTest {

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

    @Test
    void actuatorHealthIsPubliclyAccessible() throws Exception {
        this.mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anyOtherEndpointRequiresAuthenticationByDefault() throws Exception {
        // Keine fachlichen Endpunkte in diesem Arbeitspaket (folgen in AP4) - entscheidend
        // ist, dass die Security-Filterkette bereits VOR einem etwaigen 404 des (nicht
        // existierenden) Handlers greift und auf die Login-Seite umleitet.
        this.mockMvc.perform(get("/some-not-yet-existing-endpoint")).andExpect(status().is3xxRedirection());
    }

    @Test
    void formLoginWithValidCredentialsAuthenticates() throws Exception {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        String username = Fixtures.unique("web-user");
        UserEntity user = new UserEntity(Fixtures.unique("Name"), username, group);
        user.setPassword(this.passwordVerificationService.encodeNew("s3cr3t!"));
        this.userRepository.save(user);

        this.mockMvc.perform(formLogin().user(username).password("s3cr3t!")).andExpect(authenticated());
    }

    @Test
    void formLoginWithInvalidCredentialsIsRejected() throws Exception {
        this.mockMvc.perform(formLogin().user(Fixtures.unique("no-such-user")).password("wrong")).andExpect(
                unauthenticated());
    }
}
