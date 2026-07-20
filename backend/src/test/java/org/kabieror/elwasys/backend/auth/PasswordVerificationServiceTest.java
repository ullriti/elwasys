package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.HashAlgorithm;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.VerificationResult;

/**
 * Reine Unit-Tests (kein Spring-Kontext, keine DB) für die Format-Erkennung und
 * Verifikationslogik - siehe kb/05-migration-plan.md, AP3.
 */
class PasswordVerificationServiceTest {

    private final PasswordVerificationService service = new PasswordVerificationService();

    @Test
    void recognizesAndVerifiesArgon2idHashes() {
        String hash = this.service.encodeNew("correct horse battery staple");
        assertThat(hash).startsWith("$argon2id$");

        VerificationResult ok = this.service.verify("correct horse battery staple", hash);
        assertThat(ok.matches()).isTrue();
        assertThat(ok.algorithm()).isEqualTo(HashAlgorithm.ARGON2ID);

        VerificationResult wrong = this.service.verify("wrong password", hash);
        assertThat(wrong.matches()).isFalse();
        assertThat(wrong.algorithm()).isEqualTo(HashAlgorithm.ARGON2ID);
    }

    @Test
    void recognizesAndVerifiesLegacySha1Hashes() {
        // admin / SHA1("admin") - der Seed-Wert aus database-init.sql / der Flyway-Baseline.
        String legacyHash = "d033e22ae348aeb5660fc2140aec35850c4da997";

        VerificationResult ok = this.service.verify("admin", legacyHash);
        assertThat(ok.matches()).isTrue();
        assertThat(ok.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);

        VerificationResult wrong = this.service.verify("not-admin", legacyHash);
        assertThat(wrong.matches()).isFalse();
        assertThat(wrong.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);
    }

    @Test
    void legacySha1DetectionIsCaseInsensitiveForTheStoredHash() {
        String upper = "D033E22AE348AEB5660FC2140AEC35850C4DA997";

        VerificationResult ok = this.service.verify("admin", upper);

        assertThat(ok.matches()).isTrue();
        assertThat(ok.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);
    }

    @Test
    void unrecognizedOrMissingFormatsAreRejectedWithoutThrowing() {
        assertThat(this.service.verify("x", (String) null).matches()).isFalse();
        assertThat(this.service.verify("x", "").matches()).isFalse();
        assertThat(this.service.verify(null, "d033e22ae348aeb5660fc2140aec35850c4da997").matches()).isFalse();

        VerificationResult garbage = this.service.verify("x", "not-a-hash");
        assertThat(garbage.matches()).isFalse();
        assertThat(garbage.algorithm()).isEqualTo(HashAlgorithm.UNRECOGNIZED);

        // Erkennbares Argon2id-Präfix, aber verstümmelte Nutzlast: Nichttreffer statt
        // Exception.
        VerificationResult malformedArgon2 = this.service.verify("x", "$argon2id$garbage");
        assertThat(malformedArgon2.matches()).isFalse();
        assertThat(malformedArgon2.algorithm()).isEqualTo(HashAlgorithm.ARGON2ID);
    }

    @Test
    void encodedHashesFitTheWidenedPasswordColumn() {
        // Siehe db/migration/V2__widen_users_password_column.sql: die Bestandsspalte
        // "users.password" wurde von VARCHAR(50) auf VARCHAR(255) erweitert, weil
        // Argon2id-Strings mit diesen Parametern konstant 97 Zeichen lang sind.
        for (int i = 0; i < 20; i++) {
            String hash = this.service.encodeNew("password-" + i);
            assertThat(hash.length()).isLessThanOrEqualTo(255);
        }
    }
}
