package org.kabieror.elwasys.backend.auth.terminal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;
import org.kabieror.elwasys.backend.repository.TerminalTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltet Standort-Tokens für die Terminal-REST-API/den WebSocket-Endpunkt (Phase 2 AP4,
 * siehe docs/kb/05-migration-plan.md, Technologie-Entscheidung "API-Auth: Terminal statisches
 * Token pro Standort (rotierbar)").
 *
 * <p><b>Speicherung</b>: nur ein SHA-256-Hash (hex, 64 Zeichen) des Klartext-Tokens landet in
 * der DB ({@link TerminalTokenEntity#getTokenHash()}) - das Klartext-Token selbst wird
 * NIRGENDS persistiert, es existiert nur im Rückgabewert von {@link #createToken} (siehe
 * {@link IssuedTerminalToken}). Ein einfacher Hash (kein Argon2/bcrypt wie bei
 * Benutzerpasswörtern) genügt hier bewusst: das Token selbst ist bereits ein
 * hochentropisches Zufallsgeheimnis (32 Byte {@link SecureRandom}, nicht ein von Menschen
 * gewähltes Passwort), der Hash dient in erster Linie als indizierbarer, nicht im Klartext
 * lesbarer Lookup-Schlüssel - ein Offline-Brute-Force auf den Hash ist gegen 256 Bit Entropie
 * praktisch aussichtslos.
 *
 * <p><b>Rotation</b>: pro Standort sind beliebig viele aktive Tokens gleichzeitig gültig (n:1
 * zu {@link LocationEntity}) - ein neues Token für denselben Standort anzulegen widerruft
 * das alte NICHT automatisch. Der Bediener stellt das Terminal auf das neue Token um und
 * widerruft danach das alte explizit ({@link #revoke(Integer)}) - so entsteht kein
 * Ausfallfenster.
 */
@Service
public class TerminalTokenService {

    private static final String TOKEN_PREFIX = "elwt_";

    private static final int TOKEN_RANDOM_BYTES = 32;

    private final TerminalTokenRepository terminalTokenRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public TerminalTokenService(TerminalTokenRepository terminalTokenRepository) {
        this.terminalTokenRepository = terminalTokenRepository;
    }

    /**
     * Erzeugt und speichert ein neues, aktives Token für einen Standort. Das Klartext-Token
     * wird ausschließlich im Rückgabewert zurückgegeben - siehe Klassen-Javadoc.
     *
     * @param location der Standort, für den das Token gelten soll
     * @param label    optionale, rein informative Beschriftung (z.B. Terminal-Hostname), darf
     *                 {@code null} sein
     */
    @Transactional
    public IssuedTerminalToken createToken(LocationEntity location, String label) {
        String rawToken = generateRawToken();
        String hash = hash(rawToken);
        TerminalTokenEntity entity = this.terminalTokenRepository.save(new TerminalTokenEntity(location, hash, label));
        return new IssuedTerminalToken(rawToken, entity);
    }

    /**
     * Prüft ein vom Client vorgelegtes Klartext-Token: liefert das zugehörige, AKTIVE
     * {@link TerminalTokenEntity} zurück (leer bei unbekanntem oder widerrufenem Token) und
     * aktualisiert bei Erfolg {@link TerminalTokenEntity#touchLastUsed()} (rein informativ,
     * best effort).
     */
    @Transactional
    public Optional<TerminalTokenEntity> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String hash = hash(rawToken);
        Optional<TerminalTokenEntity> found = this.terminalTokenRepository.findByTokenHash(hash);
        if (found.isEmpty() || !found.get().isActive()) {
            return Optional.empty();
        }
        TerminalTokenEntity entity = found.get();
        entity.touchLastUsed();
        this.terminalTokenRepository.save(entity);
        return Optional.of(entity);
    }

    /**
     * Widerruft ein Token dauerhaft (keine Löschung - siehe {@link TerminalTokenEntity#revoke()}).
     *
     * @return {@code true}, wenn ein Token mit dieser Id existiert (unabhängig davon, ob es
     *         vorher schon widerrufen war)
     */
    @Transactional
    public boolean revoke(Integer tokenId) {
        return this.terminalTokenRepository.findById(tokenId).map(entity -> {
            entity.revoke();
            this.terminalTokenRepository.save(entity);
            return true;
        }).orElse(false);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        this.secureRandom.nextBytes(randomBytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 ist in jeder JVM garantiert vorhanden (siehe MessageDigest-Javadoc).
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
