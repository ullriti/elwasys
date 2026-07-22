package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.InvalidCurrentPasswordException;
import org.kabieror.elwasys.backend.exception.PasswordTooShortException;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link PasswordService} (Phase 3 AP4, Testfall P16, siehe
 * docs/kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../components/ChangePasswordWindow}.
 */
class PasswordServiceTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordService passwordService;
    @Autowired
    private PasswordVerificationService passwordVerificationService;

    private UserEntity newUserWithPassword(String rawPassword) {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group);
        user.setPassword(this.passwordVerificationService.encodeNew(rawPassword));
        return this.userRepository.save(user);
    }

    @Test
    void changeOwnPasswordSucceedsWithTheCorrectCurrentPassword() {
        UserEntity user = newUserWithPassword("old-secret");

        this.passwordService.changeOwnPassword(user, "old-secret", "new-secret");

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(this.passwordVerificationService.verify("new-secret", reloaded.getPassword()).matches()).isTrue();
        // P16: "erneuter Login mit neuem Passwort klappt" - das neue Passwort ist im neuen
        // Argon2id-Format gespeichert (jedes Neusetzen erzeugt laut Auftrag AP3 immer das neue
        // Format).
        assertThat(reloaded.getPassword()).startsWith("$argon2id$");
    }

    @Test
    void changeOwnPasswordFailsWithAWrongCurrentPassword() {
        UserEntity user = newUserWithPassword("old-secret");

        assertThatThrownBy(() -> this.passwordService.changeOwnPassword(user, "wrong-password", "new-secret"))
                .isInstanceOf(InvalidCurrentPasswordException.class);

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(this.passwordVerificationService.verify("old-secret", reloaded.getPassword()).matches())
                .as("password must be unchanged after a failed attempt").isTrue();
    }

    @Test
    void changeOwnPasswordAcceptsALegacySha1CurrentPassword() {
        // Ein Benutzer, dessen Hash noch nicht auf Argon2id migriert wurde (siehe
        // PasswordVerificationService), muss sein Passwort trotzdem über das aktuelle
        // SHA1-Passwort ändern können.
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group);
        // SHA1("old-secret") - siehe PasswordVerificationServiceParityTest für die exakte
        // Herleitung dieses Alt-Formats.
        user.setPassword("e114e36f81cb1f158011810e80f5a387c281410f");
        user = this.userRepository.save(user);

        this.passwordService.changeOwnPassword(user, "old-secret", "new-secret");

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).startsWith("$argon2id$");
    }

    /**
     * Issue #44 / ADR 0018 (Pre-Launch AP4): ein zu kurzes Passwort (&lt; 8 Zeichen) wird
     * zentral in {@link PasswordService#setNewPassword} abgewiesen; der bestehende Hash bleibt
     * unverändert.
     */
    @Test
    void setNewPasswordRejectsATooShortPassword() {
        UserEntity user = newUserWithPassword("old-secret");
        String originalHash = user.getPassword();

        assertThatThrownBy(() -> this.passwordService.setNewPassword(user, "short")).isInstanceOf(
                PasswordTooShortException.class);

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPassword()).as("hash must be unchanged after a rejected password").isEqualTo(
                originalHash);
    }

    @Test
    void changeOwnPasswordRejectsATooShortNewPassword() {
        UserEntity user = newUserWithPassword("old-secret");

        assertThatThrownBy(() -> this.passwordService.changeOwnPassword(user, "old-secret", "1234567")).isInstanceOf(
                PasswordTooShortException.class);
    }

    @Test
    void setNewPasswordAcceptsAPasswordAtTheMinimumLength() {
        UserEntity user = newUserWithPassword("old-secret");

        // Genau 8 Zeichen -> erlaubt.
        this.passwordService.setNewPassword(user, "12345678");

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(this.passwordVerificationService.verify("12345678", reloaded.getPassword()).matches()).isTrue();
    }

    @Test
    void setNewPasswordConsumesAnOpenResetKey() {
        UserEntity user = newUserWithPassword("old-secret");
        user.setPasswordResetKey("some-token");
        user.setPasswordResetTimeout(java.time.LocalDateTime.now().plusHours(1));
        user = this.userRepository.save(user);

        this.passwordService.setNewPassword(user, "brand-new-secret");

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPasswordResetKey()).isNull();
        assertThat(reloaded.getPasswordResetTimeout()).isNull();
        assertThat(this.passwordVerificationService.verify("brand-new-secret", reloaded.getPassword()).matches())
                .isTrue();
    }
}
