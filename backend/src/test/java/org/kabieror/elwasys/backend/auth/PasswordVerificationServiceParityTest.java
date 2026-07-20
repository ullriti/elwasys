package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.HashAlgorithm;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.VerificationResult;
import org.kabieror.elwasys.common.Utilities;

/**
 * Äquivalenznachweis Alt vs. Neu (analog AP2, siehe kb/05-migration-plan.md, AP3): ein
 * Hash, den die ECHTE Alt-Code-Routine ({@code org.kabieror.elwasys.common.Utilities#sha1},
 * aufgerufen von {@code common.User#checkPassword}/{@code #changePassword}) für ein
 * Passwort erzeugt, muss vom neuen {@link PasswordVerificationService} als gültiges
 * SHA1-Legacy-Passwort akzeptiert werden. {@code common} ist nur test-scope Abhängigkeit
 * (siehe backend/pom.xml) - keine Laufzeit-Abhängigkeit des Backends.
 */
class PasswordVerificationServiceParityTest {

    private final PasswordVerificationService service = new PasswordVerificationService();

    @Test
    void acceptsHashesProducedByTheRealLegacySha1Routine() throws Exception {
        // Bewusst inkl. eines Passworts mit Nicht-ASCII-Zeichen: prüft die in
        // PasswordVerificationService dokumentierte Charset-Annahme (UTF-8 statt des vom
        // Alt-Code verwendeten Plattform-Default-Charsets) gegen die echte Alt-Code-Routine.
        for (String password : new String[] {"admin", "correct horse battery staple", "Wäschküche42!", "a"}) {
            String legacyHash = Utilities.sha1(password);

            VerificationResult result = this.service.verify(password, legacyHash);

            assertThat(result.matches()).as("password '%s' should verify against the real legacy SHA1 hash",
                    password).isTrue();
            assertThat(result.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);
        }
    }

    @Test
    void rejectsALegacyHashProducedForADifferentPassword() throws Exception {
        String legacyHash = Utilities.sha1("correct password");

        VerificationResult result = this.service.verify("wrong password", legacyHash);

        assertThat(result.matches()).isFalse();
        assertThat(result.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);
    }
}
