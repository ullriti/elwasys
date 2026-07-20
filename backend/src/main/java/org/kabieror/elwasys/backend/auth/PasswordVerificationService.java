package org.kabieror.elwasys.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Prüft ein Klartext-Passwort gegen einen gespeicherten Hash in BEIDEN im Bestand
 * vorkommenden Formaten (siehe kb/05-migration-plan.md, AP3) und erzeugt neue Hashes
 * IMMER im neuen Format.
 *
 * <ul>
 *   <li><b>Argon2id</b> (neu, erkennbar am Präfix {@code $argon2id$}): erzeugt von
 *       {@link #encodeNew}, verifiziert über {@link Argon2PasswordEncoder} (konstante
 *       Vergleichszeit ist Teil von dessen Implementierung).</li>
 *   <li><b>SHA1</b> (Alt-Format, siehe {@code org.kabieror.elwasys.common.Utilities#sha1}
 *       und {@code common.User#checkPassword}/{@code #changePassword}): reines SHA-1 ohne
 *       Salt, 40 Zeichen Hex, klein geschrieben - erkennbar an genau diesem Muster, da
 *       Argon2id-Strings immer mit {@code $argon2id$} beginnen und daher nie mit dem
 *       SHA1-Hex-Muster kollidieren. Verifikation über einen eigenen, konstanten
 *       Byte-Vergleich ({@link MessageDigest#isEqual}) statt {@code String#equals} wie im
 *       Alt-Code (siehe Auftrag AP3: "Timing-/Sicherheitsbasics beachten").</li>
 * </ul>
 *
 * <p><b>Byte-Kodierung des Klartexts für SHA1</b>: der Alt-Code ruft {@code
 * s.getBytes()} ohne explizites Charset auf (Plattform-Default). Diese Portierung
 * verwendet explizit UTF-8 - auf allen betroffenen Alt- und Neu-JVMs (Linux, Java 8/16/21)
 * ist das ohnehin der Plattform-Default, siehe kb/05-migration-plan.md ("Beobachtungen").
 * Nur bei Passwörtern mit Nicht-ASCII-Zeichen UND einer abweichend konfigurierten
 * Alt-JVM-Locale/-Encoding könnte das theoretisch divergieren - in der Praxis nicht
 * beobachtet und über den Parity-Test gegen die echte Alt-Code-Routine abgesichert.
 */
@Service
public class PasswordVerificationService {

    private static final String ARGON2ID_PREFIX = "$argon2id$";

    private static final Pattern LEGACY_SHA1_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");

    private final Argon2PasswordEncoder argon2Encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    /**
     * Erkanntes Hash-Format eines gespeicherten Werts.
     */
    public enum HashAlgorithm {
        ARGON2ID, LEGACY_SHA1, UNRECOGNIZED
    }

    /**
     * Ergebnis einer Verifikation: ob das Passwort passt und in welchem Format der
     * geprüfte, gespeicherte Hash vorlag (wird für die Re-Hash-Entscheidung gebraucht).
     */
    public record VerificationResult(boolean matches, HashAlgorithm algorithm) {

        static VerificationResult noMatch(HashAlgorithm algorithm) {
            return new VerificationResult(false, algorithm);
        }
    }

    /**
     * Prüft {@code rawPassword} gegen den gespeicherten Hash, unabhängig davon in welchem
     * der beiden unterstützten Formate dieser vorliegt. Erkennt das Format robust: ein
     * {@code null}/leerer/unbekannt formatierter gespeicherter Wert führt zu
     * {@code matches=false}, nie zu einer Exception.
     */
    public VerificationResult verify(CharSequence rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) {
            return VerificationResult.noMatch(HashAlgorithm.UNRECOGNIZED);
        }
        if (storedHash.startsWith(ARGON2ID_PREFIX)) {
            boolean matches;
            try {
                matches = this.argon2Encoder.matches(rawPassword, storedHash);
            } catch (RuntimeException e) {
                // Erkennbares Präfix, aber kaputte Nutzlast (z.B. manuell verstümmelt) -
                // als Nichttreffer werten statt den Login-Versuch mit einer Exception
                // abzubrechen.
                matches = false;
            }
            return new VerificationResult(matches, HashAlgorithm.ARGON2ID);
        }
        if (LEGACY_SHA1_PATTERN.matcher(storedHash).matches()) {
            return new VerificationResult(legacySha1Matches(rawPassword, storedHash), HashAlgorithm.LEGACY_SHA1);
        }
        return VerificationResult.noMatch(HashAlgorithm.UNRECOGNIZED);
    }

    /**
     * Erzeugt einen neuen Hash für ein (neu gesetztes) Passwort - IMMER im Argon2id-Format,
     * unabhängig vom {@code elwasys.auth.rehash-on-login}-Flag (das steuert nur die
     * Migration BESTEHENDER SHA1-Hashes beim Login, siehe {@link AuthProperties}). Jedes
     * explizite Neusetzen eines Passworts über das Backend soll immer das neue Format
     * erzeugen (siehe Auftrag AP3).
     */
    public String encodeNew(CharSequence rawPassword) {
        return this.argon2Encoder.encode(rawPassword);
    }

    private boolean legacySha1Matches(CharSequence rawPassword, String storedHexHash) {
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(storedHexHash.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] actual = sha1(rawPassword.toString());
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * 1:1-Nachbildung von {@code org.kabieror.elwasys.common.Utilities#sha1}: reines SHA-1
     * ohne Salt. Der Alt-Code baut die Hex-Darstellung von Hand
     * ({@code Integer.toString((b & 0xff) + 0x100, 16).substring(1)} pro Byte) - das
     * ergibt exakt dieselbe klein geschriebene Zwei-Zeichen-pro-Byte-Kodierung wie
     * {@link HexFormat#formatHex}, das hier für den Vergleich stattdessen auf die
     * gespeicherte Seite angewendet wird ({@link #legacySha1Matches}).
     */
    private static byte[] sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 ist von jeder JDK-Implementierung garantiert bereitgestellt (siehe
            // java.security.MessageDigest-Javadoc) - praktisch unerreichbar.
            throw new IllegalStateException("SHA-1 MessageDigest nicht verfügbar", e);
        }
    }
}
