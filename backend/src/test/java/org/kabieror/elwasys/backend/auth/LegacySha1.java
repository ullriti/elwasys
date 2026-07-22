package org.kabieror.elwasys.backend.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Test-Helfer, der das SHA1-Hash-Verfahren des abgelösten Alt-Portals
 * ({@code org.kabieror.elwasys.common.Utilities#sha1}) byte-für-byte reproduziert.
 *
 * <p>Das ehemalige {@code common}-Modul wurde nach Abschluss der Migration aufgelöst
 * (die sechs Utility-Klassen leben jetzt im Client-Raspi-Modul, siehe
 * docs/kb/05-migration-plan.md). Das Backend hat zur Laufzeit ohnehin nie von {@code common}
 * abgehängt; die Auth-Parity-Tests brauchen aber weiterhin genau das Alt-Hash-Format,
 * um zu belegen, dass {@code PasswordVerificationService} Bestandshashes (SHA1, hex,
 * Kleinbuchstaben, ohne Salt) korrekt verifiziert. Diese Kopie hält den Algorithmus
 * identisch fest – reines SHA-1 über {@code s.getBytes()} (Plattform-Default-Charset,
 * exakt wie im Alt-Code), Hex mit führenden Nullen.
 */
final class LegacySha1 {

    private LegacySha1() {
    }

    static String sha1(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        final byte[] b = md.digest(s.getBytes());
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }
}
