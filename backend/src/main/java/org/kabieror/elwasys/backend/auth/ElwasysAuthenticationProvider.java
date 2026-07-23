package org.kabieror.elwasys.backend.auth;

import java.time.LocalDateTime;
import java.util.Locale;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.HashAlgorithm;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.VerificationResult;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.RateLimiter;
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
 * Admin-Portal (Phase 3, siehe docs/kb/05-migration-plan.md, AP3). Fachlicher Nachfolger von
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
 *       docs/kb/05-migration-plan.md ("Entscheidungen") für die vollständige Abwägung.</li>
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

    private final RateLimiter rateLimiter;

    public ElwasysAuthenticationProvider(UserRepository userRepository,
            PasswordVerificationService passwordVerificationService, AuthProperties authProperties,
            RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordVerificationService = passwordVerificationService;
        this.authProperties = authProperties;
        this.rateLimiter = rateLimiter;
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

        // Brute-Force-Schutz (Issue #25, Pre-Launch AP4): steht ein Benutzername wegen zu
        // vieler jüngster Fehlversuche unter temporärer Sperre, wird der Versuch abgewiesen -
        // bewusst VOR der Passwortprüfung, damit die Sperre auch bei (dann) korrektem Passwort
        // greift. Der Zähler wird nur für tatsächlich existierende Benutzer geführt (siehe
        // unten), daher trifft die Sperre nie einen unbekannten Namen und wächst der
        // Zähler-Speicher nicht durch beliebige Fantasie-Namen.
        //
        // Bewusst dieselbe generische Exception/Meldung wie ein normaler Fehlversuch (NICHT eine
        // eigene "zu viele Versuche"-Meldung): weil der Zähler nur für existierende Benutzer
        // geführt wird, wäre eine unterscheidbare Sperr-Meldung ein Enumeration-Orakel (nur reale
        // Konten ließen sich "sperren" und damit erkennen) - genau das, was die generische Meldung
        // und ADR 0018 vermeiden wollen. Die Sperre drosselt also, ohne die Kontenexistenz zu
        // verraten (Code-Review-Befund AP4). Das verbleibende Timing-Orakel - ein unbekannter Name
        // kehrt ohne Argon2-Verifikation schneller zurück - ist ein bewusst akzeptiertes
        // Restrisiko, siehe ADR 0018.
        String lockKey = loginKey(username);
        if (this.rateLimiter.currentCount(lockKey, this.authProperties.getLoginLockoutWindow())
                >= this.authProperties.getMaxFailedLoginAttempts()) {
            LOG.warn("Login for user '{}' temporarily locked after too many failed attempts.", username);
            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        UserEntity user = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse(username).orElse(null);
        if (user == null || user.getPassword() == null || user.getPassword().isEmpty()) {
            // Generische Meldung mit Absicht: nicht verraten, ob der Benutzername überhaupt
            // existiert (Alt-Code tut das durch die identische Rückgabe "false" für "nicht
            // gefunden" und "Passwort falsch" implizit ebenfalls). Bewusst KEIN Fehlversuch
            // gezählt: ein unbekannter Name soll keinen (attacker-kontrollierten) Zähler-
            // Eintrag anlegen können.
            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        VerificationResult result = this.passwordVerificationService.verify(rawPassword, user.getPassword());
        if (!result.matches()) {
            this.rateLimiter.increment(lockKey, this.authProperties.getLoginLockoutWindow());
            throw new BadCredentialsException(GENERIC_FAILURE_MESSAGE);
        }

        // Bewusst NACH der Passwortprüfung: nur wer das korrekte Passwort kennt, erfährt
        // über die andere Exception-Art, dass das Konto gesperrt ist (siehe Klassen-
        // Javadoc für die Abweichung vom Alt-Code).
        if (user.isBlocked()) {
            throw new LockedException("Der Benutzer ist gesperrt.");
        }

        // Erfolgreicher Login: den Fehlversuchs-Zähler dieses Benutzers zurücksetzen, damit
        // frühere Vertipper ihn nicht dauerhaft belasten.
        this.rateLimiter.reset(lockKey);

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

    /**
     * Schlüssel des Fehlversuchs-Zählers: der case-insensitiv normalisierte Benutzername
     * (der Login vergleicht ebenfalls case-insensitiv, "Anna" und "anna" teilen sich also
     * denselben Zähler). Eine zusätzliche Quell-IP-Komponente ist bewusst NICHT verdrahtet -
     * der {@link org.springframework.security.authentication.AuthenticationProvider} hat keinen
     * direkten Request-Zugriff, und für die Einzelinstanz genügt die Sperre pro Benutzername
     * (siehe ADR 0018 / docs/kb/05-migration-plan.md).
     */
    private static String loginKey(String username) {
        return "login:" + username.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
