package org.kabieror.elwasys.backend.auth;

import java.time.LocalDateTime;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.HashAlgorithm;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.VerificationResult;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentifiziert gegen die Bestandstabelle {@code users} (über {@link UserRepository} +
 * {@link PasswordVerificationService}) - fachliche Grundlage für das künftige Vaadin-Flow-
 * Admin-Portal (Phase 3, siehe kb/05-migration-plan.md, AP3). Fachlicher Nachfolger von
 * {@code Portal/.../SessionManager#login} + {@code common.User#checkPassword}.
 *
 * <p><b>Regeln, aus dem Alt-Portal-Code nachvollzogen</b> ({@code SessionManager#login}
 * iteriert über {@code WashportalManager.instance.getDataManager().getUsers()}, was intern
 * {@code SELECT * FROM users WHERE deleted=FALSE} ausführt):
 * <ul>
 *   <li>Benutzername-Vergleich case-insensitiv ({@code equalsIgnoreCase}) - hier über
 *       {@link UserRepository#findByUsernameIgnoreCaseAndDeletedFalse}.</li>
 *   <li>Gelöschte Benutzer ({@code deleted=true}) sind nicht anmeldbar - 1:1 wie der
 *       Alt-Code (der sie über die {@code WHERE deleted=FALSE}-Klausel gar nicht erst
 *       lädt).</li>
 *   <li><b>Bewusste Abweichung vom Alt-Code:</b> {@code SessionManager#login} prüft
 *       {@code user.isBlocked()} NICHT - diese Prüfung existiert im Bestand nur beim
 *       Terminal-Kartenlogin ({@code Client-Raspi/.../MainFormController#onCardDetected},
 *       nachgebildet in {@link org.kabieror.elwasys.backend.service.PermissionService}).
 *       Ein gesperrter Benutzer konnte sich im Alt-Portal also weiterhin per Passwort
 *       einloggen (nur die Ausführungs-/Standort-Berechtigungen greifen dort separat).
 *       Der Auftrag für dieses Arbeitspaket (AP3) verlangt explizit "gesperrte/deaktivierte
 *       Nutzer werden abgewiesen" für das NEUE Portal-Fundament - diese Implementierung
 *       setzt das um und weist gesperrte Benutzer aktiv ab. Das ist damit KEINE 1:1-
 *       Verhaltensbewahrung, sondern eine bewusste, hier dokumentierte Verschärfung; siehe
 *       kb/05-migration-plan.md ("Entscheidungen") für die vollständige Abwägung.</li>
 * </ul>
 *
 * <p>Bei erfolgreicher Anmeldung wird zusätzlich {@code last_login} aktualisiert - 1:1 wie
 * {@code common.User#updateLastLogin} (vom Alt-Portal nach jedem erfolgreichen Login
 * aufgerufen).
 */
@Component
public class ElwasysAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ElwasysAuthenticationProvider.class);

    private static final String GENERIC_FAILURE_MESSAGE = "Benutzername oder Passwort ist falsch.";

    private final UserRepository userRepository;

    private final PasswordVerificationService passwordVerificationService;

    private final AuthProperties authProperties;

    public ElwasysAuthenticationProvider(UserRepository userRepository,
            PasswordVerificationService passwordVerificationService, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordVerificationService = passwordVerificationService;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        String rawPassword = credentials == null ? null : credentials.toString();

        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        UserEntity user = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse(username).orElse(null);
        if (user == null || user.getPassword() == null || user.getPassword().isEmpty()) {
            // Generische Meldung mit Absicht: nicht verraten, ob der Benutzername überhaupt
            // existiert (Alt-Code tut das durch die identische Rückgabe "false" für "nicht
            // gefunden" und "Passwort falsch" implizit ebenfalls).
            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        VerificationResult result = this.passwordVerificationService.verify(rawPassword, user.getPassword());
        if (!result.matches()) {
            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        // Bewusst NACH der Passwortprüfung: nur wer das korrekte Passwort kennt, erfährt
        // über die andere Exception-Art, dass das Konto gesperrt ist (siehe Klassen-
        // Javadoc für die Abweichung vom Alt-Code).
        if (user.isBlocked()) {
            throw new LockedException("Der Benutzer ist gesperrt.");
        }

        if (result.algorithm() == HashAlgorithm.LEGACY_SHA1 && this.authProperties.isRehashOnLogin()) {
            LOG.info("Migrating password hash of user '{}' (id={}) from SHA1 to Argon2id on login.",
                    user.getUsername(), user.getId());
            user.setPassword(this.passwordVerificationService.encodeNew(rawPassword));
        }

        user.setLastLogin(LocalDateTime.now());
        this.userRepository.save(user);

        ElwasysUserPrincipal principal = new ElwasysUserPrincipal(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
