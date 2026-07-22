package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Integrationstests für {@link ElwasysAuthenticationProvider} gegen ein echtes PostgreSQL
 * (siehe kb/05-migration-plan.md, AP3). Diese Klasse deckt den PRODUKTIVEN Default-Zustand
 * ab ({@code elwasys.auth.rehash-on-login=false}, siehe application.yml) - für den
 * eingeschalteten Migrationspfad siehe {@link ElwasysAuthenticationProviderRehashEnabledTest}.
 */
class ElwasysAuthenticationProviderTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ElwasysAuthenticationProvider provider;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private PasswordVerificationService passwordVerificationService;

    private UserGroupEntity group() {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
    }

    private UserEntity userWithLegacyPassword(String username, String rawPassword, boolean blocked) throws Exception {
        UserEntity user = new UserEntity(Fixtures.unique("Name"), username, group());
        user.setPassword(LegacySha1.sha1(rawPassword));
        user.setBlocked(blocked);
        return this.userRepository.save(user);
    }

    private Authentication attempt(String username, String password) {
        return this.provider.authenticate(new UsernamePasswordAuthenticationToken(username, password));
    }

    @Test
    void rehashOnLoginDefaultsToFalse() {
        // Zusicherung, dass der harte Rahmenbedingungs-Default nicht versehentlich per
        // application.yml/Umgebungsvariable umgekippt wurde - siehe kb/05-migration-plan.md,
        // "Entscheidungen".
        assertThat(this.authProperties.isRehashOnLogin()).isFalse();
    }

    @Test
    void legacySha1UserCanAuthenticateAndUsernameComparisonIsCaseInsensitive() throws Exception {
        String username = Fixtures.unique("sha1-user");
        userWithLegacyPassword(username, "s3cr3t!", false);

        Authentication auth = attempt(username.toUpperCase(Locale.ROOT), "s3cr3t!");

        assertThat(auth.isAuthenticated()).isTrue();
        ElwasysUserPrincipal principal = (ElwasysUserPrincipal) auth.getPrincipal();
        assertThat(principal.getUsername()).isEqualTo(username);
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    void adminFlagBecomesRoleAdmin() throws Exception {
        String username = Fixtures.unique("admin-user");
        UserEntity user = userWithLegacyPassword(username, "s3cr3t!", false);
        user.setAdmin(true);
        this.userRepository.save(user);

        Authentication auth = attempt(username, "s3cr3t!");

        ElwasysUserPrincipal principal = (ElwasysUserPrincipal) auth.getPrincipal();
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_ADMIN",
                "ROLE_USER");
    }

    @Test
    void rehashFlagDisabledLeavesStoredHashByteIdenticalSha1() throws Exception {
        String username = Fixtures.unique("sha1-user");
        String rawPassword = "s3cr3t!";
        UserEntity created = userWithLegacyPassword(username, rawPassword, false);
        String originalHash = created.getPassword();

        attempt(username, rawPassword);

        UserEntity reloaded = this.userRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).as(
                "Parallelbetriebs-Beweis: Hash bleibt SHA1, das Alt-Portal kann den Benutzer weiterhin "
                        + "verifizieren").isEqualTo(originalHash);
        assertThat(reloaded.getPassword()).isEqualTo(LegacySha1.sha1(rawPassword));
    }

    @Test
    void successfulLoginUpdatesLastLoginLikeTheLegacyPortal() throws Exception {
        String username = Fixtures.unique("sha1-user");
        UserEntity created = userWithLegacyPassword(username, "s3cr3t!", false);
        assertThat(created.getLastLogin()).isNull();

        attempt(username, "s3cr3t!");

        UserEntity reloaded = this.userRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getLastLogin()).isNotNull();
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        String username = Fixtures.unique("sha1-user");
        userWithLegacyPassword(username, "s3cr3t!", false);

        assertThatThrownBy(() -> attempt(username, "wrong-password")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void unknownUsernameIsRejectedWithTheSameGenericExceptionAsWrongPassword() {
        assertThatThrownBy(() -> attempt(Fixtures.unique("no-such-user"), "whatever")).isInstanceOf(
                BadCredentialsException.class);
    }

    @Test
    void blockedUserIsRejectedEvenWithCorrectPassword() throws Exception {
        // Bewusste Abweichung vom Alt-Portal-Login (der prüft isBlocked() NICHT, siehe
        // ElwasysAuthenticationProvider-Javadoc) - für dieses neue Login-Fundament verlangt
        // AP3 explizit die Ablehnung gesperrter Benutzer.
        String username = Fixtures.unique("blocked-user");
        userWithLegacyPassword(username, "s3cr3t!", true);

        assertThatThrownBy(() -> attempt(username, "s3cr3t!")).isInstanceOf(LockedException.class);
    }

    @Test
    void deletedUserIsRejectedLikeAnUnknownUsername() throws Exception {
        String username = Fixtures.unique("deleted-user");
        UserEntity user = userWithLegacyPassword(username, "s3cr3t!", false);
        user.setDeleted(true);
        this.userRepository.save(user);

        assertThatThrownBy(() -> attempt(username, "s3cr3t!")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void argon2idUserCanAuthenticateAndHashFitsTheWidenedColumn() {
        String username = Fixtures.unique("argon2-user");
        UserEntity user = new UserEntity(Fixtures.unique("Name"), username, group());
        String hash = this.passwordVerificationService.encodeNew("s3cr3t!");
        user.setPassword(hash);
        this.userRepository.save(user);

        // Beweist gleichzeitig, dass der Argon2id-Hash (97 Zeichen, siehe
        // V2__widen_users_password_column.sql) tatsächlich in der jetzt VARCHAR(255)
        // breiten Spalte persistiert wurde, ohne abgeschnitten zu werden.
        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).isEqualTo(hash);

        Authentication auth = attempt(username, "s3cr3t!");
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    void malformedStoredHashIsRejectedWithoutThrowingAnUnexpectedException() {
        String username = Fixtures.unique("garbage-user");
        UserEntity user = new UserEntity(Fixtures.unique("Name"), username, group());
        user.setPassword("not-a-recognizable-hash-format");
        this.userRepository.save(user);

        assertThatThrownBy(() -> attempt(username, "whatever")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void emptyUsernameOrPasswordIsRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> attempt("", "whatever")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> attempt("someone", "")).isInstanceOf(BadCredentialsException.class);
    }
}
