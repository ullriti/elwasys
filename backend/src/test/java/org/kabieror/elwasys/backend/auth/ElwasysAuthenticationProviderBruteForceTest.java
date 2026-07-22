package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.RateLimiter;
import org.kabieror.elwasys.backend.support.MutableClock;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Brute-Force-Schutz des Portal-Logins (Issue #25, Pre-Launch AP4): nach einer konfigurierten
 * Zahl von Fehlversuchen wird der Benutzername temporär gesperrt – auch bei anschließend
 * korrektem Passwort –, nach Ablauf des Zeitfensters ist ein Login wieder möglich.
 *
 * <p>Bewusst ein reiner Unit-Test (kein Spring-Kontext): der {@link RateLimiter} wird mit einer
 * vorstellbaren/vorrückbaren {@link MutableClock} konstruiert, damit der Fensterablauf ohne
 * {@code sleep} deterministisch geprüft werden kann (das Integrationsverhalten gegen die echte
 * DB deckt {@link ElwasysAuthenticationProviderTest} ab).
 */
class ElwasysAuthenticationProviderBruteForceTest {

    private static final String USERNAME = "alice";
    private static final String CORRECT_PASSWORD = "correct-horse";

    private MutableClock clock;
    private AuthProperties authProperties;
    private ElwasysAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordVerificationService passwordVerificationService = new PasswordVerificationService();
        this.authProperties = new AuthProperties(); // Defaults: 5 Versuche / 15 min
        this.clock = new MutableClock(Instant.parse("2026-07-22T08:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(this.clock);
        this.provider = new ElwasysAuthenticationProvider(userRepository, passwordVerificationService,
                this.authProperties, rateLimiter);

        UserEntity user = new UserEntity("Alice", USERNAME,
                new UserGroupEntity("g", DiscountType.NONE, 0));
        user.setPassword(passwordVerificationService.encodeNew(CORRECT_PASSWORD));
        when(userRepository.findByUsernameIgnoreCaseAndDeletedFalse(anyString())).thenReturn(Optional.of(user));
    }

    private Authentication attempt(String password) {
        return this.provider.authenticate(new UsernamePasswordAuthenticationToken(USERNAME, password));
    }

    @Test
    void locksOutAfterTooManyFailedAttemptsEvenWithCorrectPasswordAndUnlocksAfterTheWindow() {
        // N Fehlversuche (N = Default 5) verbrauchen das Kontingent.
        for (int i = 0; i < this.authProperties.getMaxFailedLoginAttempts(); i++) {
            assertThatThrownBy(() -> attempt("wrong-password")).isInstanceOf(BadCredentialsException.class);
        }

        // Jetzt gesperrt: selbst das KORREKTE Passwort wird abgewiesen (Sperre greift vor der
        // Passwortprüfung). Bewusst eine generische BadCredentialsException, KEINE eigene
        // LockedException - siehe Anti-Enumeration-Test unten.
        assertThatThrownBy(() -> attempt(CORRECT_PASSWORD)).isInstanceOf(BadCredentialsException.class);

        // Nach Ablauf des Zeitfensters ist der Login wieder möglich.
        this.clock.advance(this.authProperties.getLoginLockoutWindow().plus(Duration.ofSeconds(1)));
        Authentication auth = attempt(CORRECT_PASSWORD);
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void lockoutIsIndistinguishableFromAWrongPasswordFailure() {
        // Ein normaler Fehlversuch liefert die generische Meldung.
        String wrongPasswordMessage =
                catchThrowable(() -> attempt("wrong-password")).getMessage();

        // Weitere Fehlversuche bis zur Sperre.
        for (int i = 1; i < this.authProperties.getMaxFailedLoginAttempts(); i++) {
            catchThrowable(() -> attempt("wrong-password"));
        }

        // Der gesperrte Versuch (mit korrektem Passwort) liefert exakt dieselbe Exception-Art und
        // -Meldung wie ein falsches Passwort. Andernfalls wäre "gesperrt" - da der Zähler nur für
        // existierende Konten geführt wird - ein Enumeration-Orakel (Regression-Charakter: mit
        // einer eigenen LockedException/Meldung schlüge diese Zusicherung fehl).
        Throwable locked = catchThrowable(() -> attempt(CORRECT_PASSWORD));
        assertThat(locked).isInstanceOf(BadCredentialsException.class);
        assertThat(locked.getMessage()).isEqualTo(wrongPasswordMessage);
    }

    @Test
    void aSuccessfulLoginResetsTheFailureCounter() {
        // Vier Fehlversuche (einer unter der Schwelle von 5).
        for (int i = 0; i < this.authProperties.getMaxFailedLoginAttempts() - 1; i++) {
            assertThatThrownBy(() -> attempt("wrong-password")).isInstanceOf(BadCredentialsException.class);
        }

        // Ein erfolgreicher Login setzt den Zähler zurück...
        assertThat(attempt(CORRECT_PASSWORD).isAuthenticated()).isTrue();

        // ...sodass danach wieder die volle Zahl an Fehlversuchen möglich ist, ohne dass die
        // Sperre (die bei kumulativer Zählung längst gegriffen hätte) auslöst.
        for (int i = 0; i < this.authProperties.getMaxFailedLoginAttempts() - 1; i++) {
            assertThatThrownBy(() -> attempt("wrong-password")).isInstanceOf(BadCredentialsException.class);
        }
        assertThat(attempt(CORRECT_PASSWORD).isAuthenticated()).isTrue();
    }
}
