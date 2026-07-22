package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;

/**
 * Deckt den eingeschalteten SHA1-&gt;Argon2id-Migrationspfad ab
 * ({@code elwasys.auth.rehash-on-login=true}) - siehe docs/kb/05-migration-plan.md, AP3
 * "Entscheidungen": das Flag ist produktiv per Default AUS (siehe
 * {@link ElwasysAuthenticationProviderTest#rehashOnLoginDefaultsToFalse}), wird hier aber
 * gezielt für diese Testklasse eingeschaltet, um sowohl den Migrationspfad selbst als auch
 * das Parallelbetriebs-Risiko zu beweisen, das ihn produktiv ausgeschaltet hält (ein
 * Re-Hash sperrt denselben Benutzer im weiterhin SHA1-vergleichenden Alt-Portal aus).
 */
@TestPropertySource(properties = "elwasys.auth.rehash-on-login=true")
class ElwasysAuthenticationProviderRehashEnabledTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ElwasysAuthenticationProvider provider;

    @Autowired
    private AuthProperties authProperties;

    @Test
    void flagIsActuallyEnabledInThisTestContext() {
        assertThat(this.authProperties.isRehashOnLogin()).isTrue();
    }

    @Test
    void successfulLegacyLoginMigratesToArgon2idAndSubsequentLoginsStillWork() throws Exception {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        String username = Fixtures.unique("migrating-user");
        String rawPassword = "s3cr3t!";
        UserEntity user = new UserEntity(Fixtures.unique("Name"), username, group);
        String legacyHash = LegacySha1.sha1(rawPassword);
        user.setPassword(legacyHash);
        user = this.userRepository.save(user);

        Authentication firstLogin = this.provider.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword));
        assertThat(firstLogin.isAuthenticated()).isTrue();

        UserEntity afterFirstLogin = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterFirstLogin.getPassword()).as("Hash wurde beim erfolgreichen Login auf Argon2id migriert")
                .startsWith("$argon2id$");
        assertThat(afterFirstLogin.getPassword()).isNotEqualTo(legacyHash);

        // Parallelbetriebs-Risiko konkret bewiesen (siehe AuthProperties-Javadoc): das
        // Alt-Portal vergleicht storedHash.equals(LegacySha1.sha1(password))
        // (common.User#checkPassword) - das schlägt jetzt fehl, der Benutzer wäre im
        // Alt-Portal ausgesperrt. Genau deshalb ist das Flag produktiv per Default AUS.
        boolean legacyPortalWouldStillAccept = afterFirstLogin.getPassword().equals(LegacySha1.sha1(rawPassword));
        assertThat(legacyPortalWouldStillAccept).as("Alt-Portal-SHA1-Login würde nach der Migration fehlschlagen")
                .isFalse();

        // Login mit demselben Passwort funktioniert im NEUEN Backend weiterhin (jetzt über
        // den Argon2id-Pfad, kein erneuter Re-Hash nötig).
        Authentication secondLogin = this.provider.authenticate(
                new UsernamePasswordAuthenticationToken(username, rawPassword));
        assertThat(secondLogin.isAuthenticated()).isTrue();

        UserEntity afterSecondLogin = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(afterSecondLogin.getPassword()).as("kein erneuter Re-Hash bei bereits migriertem Argon2id-Hash")
                .isEqualTo(afterFirstLogin.getPassword());
    }
}
