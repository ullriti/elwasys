package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.HashAlgorithm;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.VerificationResult;

/**
 * Äquivalenznachweis Alt vs. Neu (analog AP2, siehe docs/kb/05-migration-plan.md, AP3): ein
 * Hash, den das Alt-Portal-SHA1-Verfahren (im Alt-Code {@code Utilities#sha1}, aufgerufen
 * von {@code User#checkPassword}/{@code #changePassword}) für ein Passwort erzeugt, muss
 * vom neuen {@link PasswordVerificationService} als gültiges SHA1-Legacy-Passwort
 * akzeptiert werden. Das abgelöste {@code common}-Modul wurde nach der Migration aufgelöst;
 * das Alt-Hash-Format wird für diesen Test byte-genau von {@link LegacySha1} reproduziert
 * (das Backend hatte nie eine Laufzeit-Abhängigkeit auf {@code common}).
 */
class PasswordVerificationServiceParityTest {

    private final PasswordVerificationService service = new PasswordVerificationService();

    @Test
    void acceptsHashesProducedByTheRealLegacySha1Routine() throws Exception {
        // Bewusst inkl. eines Passworts mit Nicht-ASCII-Zeichen: prüft die in
        // PasswordVerificationService dokumentierte Charset-Annahme (UTF-8 statt des vom
        // Alt-Code verwendeten Plattform-Default-Charsets) gegen die echte Alt-Code-Routine.
        for (String password : new String[] {"admin", "correct horse battery staple", "Wäschküche42!", "a"}) {
            String legacyHash = LegacySha1.sha1(password);

            VerificationResult result = this.service.verify(password, legacyHash);

            assertThat(result.matches()).as("password '%s' should verify against the real legacy SHA1 hash",
                    password).isTrue();
            assertThat(result.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);
        }
    }

    @Test
    void rejectsALegacyHashProducedForADifferentPassword() throws Exception {
        String legacyHash = LegacySha1.sha1("correct password");

        VerificationResult result = this.service.verify("wrong password", legacyHash);

        assertThat(result.matches()).isFalse();
        assertThat(result.algorithm()).isEqualTo(HashAlgorithm.LEGACY_SHA1);
    }
}
