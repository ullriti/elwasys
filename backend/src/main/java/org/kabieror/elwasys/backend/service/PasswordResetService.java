package org.kabieror.elwasys.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.kabieror.elwasys.backend.auth.PasswordResetProperties;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.exception.InvalidOrExpiredResetTokenException;
import org.kabieror.elwasys.backend.notification.NotificationService;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passwort-Reset per Email (Phase 3 AP4, siehe docs/kb/05-migration-plan.md, docs/kb/03-modules.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/PasswordForgotWindow} +
 * {@code ResetPasswordWindow} (Selbstbedienung, Testfall P19) sowie des
 * "Sende dem Benutzer per Email ein neues Passwort"-Teils von
 * {@code Portal/.../components/UserWindow} (Admin-Aktion).
 *
 * <p><b>Speicherung des Schlüssels</b>: nutzt bewusst die BESTEHENDEN Spalten
 * {@code users.password_reset_key}/{@code users.password_reset_timeout} (Teil der
 * Flyway-Baseline {@code V1__baseline_schema_0_4_0.sql}, 1:1 aus dem Alt-Schema übernommen,
 * siehe docs/kb/02-data-model.md) statt einer neuen Migration - diese Spalten existieren im
 * Bestand bereits (der Alt-Code selbst nutzt sie über {@code common.User#generatePasswordResetKey}/
 * {@code #passwordResetKeyIsValid}), sind aber bisher von keiner Backend-Entity gelesen/
 * geschrieben worden. Kein Konflikt mit dem Alt-Portal: dessen eigener Reset-Flow bleibt
 * unverändert nutzbar (andere, unabhängige Spalten-Werte je nachdem, welcher der beiden
 * Portale zuletzt einen Reset ausgelöst hat - ein zur selben Zeit gültiger Schlüssel eines
 * beliebigen Portals funktioniert dabei plausibel auch im jeweils anderen, weil beide dieselbe
 * Spalte lesen/schreiben; das ist unschädlich, weil ein Reset-Schlüssel ohnehin Einmalgebrauch
 * ist und pro Anfrage neu erzeugt wird).
 *
 * <p><b>Schlüsselformat</b>: anders als der Alt-Code (SHA1 von 30 Zufalls-Bytes, faktisch nur
 * 29 echte Zufallsbytes - siehe {@code common.User#generatePasswordResetKey}, Schleife startet
 * bei Index 1) erzeugt diese Portierung den Schlüssel direkt aus
 * {@link SecureRandom} (24 Bytes, Base64-URL-sicher kodiert ohne Padding, 32 Zeichen) - ein
 * kryptographisch stärkerer, direkter Zufallswert statt eines SHA1-Hashs über einen schwachen
 * Zufallsgenerator ({@code Math.random()}). Passt weiterhin in die bestehende
 * {@code VARCHAR(50)}-Spalte.
 *
 * <p><b>Gültigkeitsdauer</b>: konfigurierbar über {@link PasswordResetProperties#getTokenValidity()},
 * Default 2 Stunden - 1:1 wie der Alt-Code ({@code LocalDateTime.now().plus(2, ChronoUnit.HOURS)}).
 */
@Service
public class PasswordResetService {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    private final NotificationService notificationService;

    private final PasswordResetProperties properties;

    private final RateLimiter rateLimiter;

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordService passwordService,
            NotificationService notificationService, PasswordResetProperties properties, RateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.notificationService = notificationService;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Öffentlicher (anonym erreichbarer) Passwort-Reset – fachlicher Nachfolger von
     * {@code PasswordForgotWindow#execute}, mit zwei bewussten Härtungen (Pre-Launch AP4,
     * ADR 0018):
     *
     * <ul>
     *   <li><b>Kontenexistenz nicht preisgeben (#24):</b> ist zu der Adresse KEIN Konto
     *       vorhanden, endet die Methode <b>still</b> (keine Exception, kein Versand). Die
     *       frühere {@code UserNotFoundForEmailException} entfällt aus diesem öffentlichen
     *       Pfad; der Dialog zeigt in JEDEM Fall dieselbe neutrale Meldung. Bewusste
     *       Abweichung vom Alt-Portal, in ADR 0018 freigegeben.</li>
     *   <li><b>Ratenlimit (#24):</b> pro Adresse wird frühestens nach
     *       {@link PasswordResetProperties#getSendCooldown()} erneut versandt (In-Memory-
     *       {@link RateLimiter}, verhindert Mail-Bombing).</li>
     * </ul>
     *
     * <p><b>Mehrere Konten je Adresse (#47):</b> {@code users.email} ist nicht eindeutig –
     * teilen sich mehrere (nicht gelöschte) Konten die Adresse, erhält JEDES seinen eigenen
     * Reset-Token und seine eigene Mail. Das fügt sich in die Neutralisierung: der Anfragende
     * erfährt weder Anzahl noch Existenz der Konten.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        List<UserEntity> users = this.userRepository.findByEmailIgnoreCaseAndDeletedFalse(email);
        if (users.isEmpty()) {
            // Neutralisierung (#24): unbekannte Adresse -> still beenden, kein Versand.
            return;
        }

        // Ratenlimit (#24): frühestens alle N Minuten ein Versand je Adresse. Der Schlüssel
        // ist die normalisierte Adresse (nicht das einzelne Konto), damit mehrere Konten
        // derselben Adresse (#47) zusammen als EIN Versandvorgang zählen. Bewusst nur PRÜFEN,
        // nicht schon hochzählen - das Fenster wird erst nach erfolgreichem Versand markiert
        // (siehe unten).
        String rateKey = "password-reset:" + email.toLowerCase(Locale.ROOT);
        if (this.rateLimiter.currentCount(rateKey, this.properties.getSendCooldown()) > 0) {
            LOG.info("Password reset for an address requested again within the cooldown window - skipping resend.");
            return;
        }

        for (UserEntity user : users) {
            String token = generateToken();
            user.setPasswordResetKey(token);
            user.setPasswordResetTimeout(LocalDateTime.now().plus(this.properties.getTokenValidity()));
            this.userRepository.save(user);

            String resetUrl = this.properties.getPortalBaseUrl() + "/reset-password?key=" + token;
            this.notificationService.sendPasswordResetEmail(user, resetUrl);
        }

        // Erst NACH erfolgreichem Versand das Cooldown-Fenster markieren: schlägt der Mailversand
        // fehl (SMTP), wird der Zähler nicht gesetzt und ein legitimer Wiederholungsversuch bleibt
        // sofort möglich, statt für die volle Cooldown-Dauer blockiert zu sein (Code-Review-Befund
        // AP4). Ein seltenes nebenläufiges Doppel-Anfragen kann so höchstens eine zweite Reset-Mail
        // auslösen - unkritisch, da mehrere Mails je Adresse ohnehin möglich sind (#47).
        this.rateLimiter.increment(rateKey, this.properties.getSendCooldown());
    }

    /**
     * 1:1-Portierung von {@code ResetPasswordWindow#save} + der Gültigkeitsprüfung aus
     * {@code common.User#passwordResetKeyIsValid} - setzt das neue Passwort, wenn der
     * Schlüssel bekannt und nicht abgelaufen ist. Der Schlüssel wird dabei über
     * {@link PasswordService#setNewPassword} konsumiert (Einmalverwendung, 1:1 wie
     * {@code common.User#changePassword}).
     *
     * @throws InvalidOrExpiredResetTokenException wenn der Schlüssel unbekannt oder abgelaufen ist
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        UserEntity user = findValidToken(token).orElseThrow(InvalidOrExpiredResetTokenException::new);
        this.passwordService.setNewPassword(user, newPassword);
    }

    /**
     * Prüft (ohne Nebenwirkung), ob ein Reset-Schlüssel aktuell gültig ist - für die
     * öffentliche Reset-Ansicht, die vor der Anzeige des Formulars prüfen möchte, ob der
     * mitgegebene Link überhaupt (noch) gültig ist.
     */
    @Transactional(readOnly = true)
    public boolean isValidToken(String token) {
        return findValidToken(token).isPresent();
    }

    private Optional<UserEntity> findValidToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return this.userRepository.findByPasswordResetKeyAndDeletedFalse(token)
                .filter(user -> user.getPasswordResetTimeout() != null && user.getPasswordResetTimeout()
                        .isAfter(LocalDateTime.now()));
    }

    /**
     * Admin-seitiger Passwort-Reset (fachlicher Nachfolger des "Sende dem Benutzer per Email
     * ein neues Passwort"-Kontrollkästchens in {@code Portal/.../components/UserWindow}):
     * generiert - anders als der Selbstbedienungs-Flow oben - SOFORT ein neues, zufälliges
     * Passwort (kein Link/Formular) und verschickt es per Email. 1:1-Portierung von
     * {@code UserWindow#save} (Zweig {@code cbSendPassword}).
     */
    @Transactional
    public void resetPasswordByAdminAndNotify(UserEntity user) {
        String newPassword = generateReadablePassword();
        this.passwordService.setNewPassword(user, newPassword);
        this.notificationService.sendNewPasswordEmail(user, newPassword);
    }

    /**
     * URL-sicherer Zufallstoken, siehe Klassen-Javadoc ("Schlüsselformat").
     */
    private String generateToken() {
        byte[] bytes = new byte[24];
        this.secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 1:1-Portierung von {@code common.Utilities#generatePassword()}: 12 Zeichen aus einem
     * für Menschen gut lesbaren Alphabet (Klein-/Großbuchstaben, Ziffern, einige
     * Sonderzeichen) - im Gegensatz zu {@link #generateToken()} bewusst KEIN URL-sicheres
     * Format, weil dieses Passwort per Email im Klartext gelesen und manuell eingetippt
     * werden soll, nicht Teil einer URL ist.
     */
    private String generateReadablePassword() {
        // Issue #46 (Pre-Launch AP4): das Alphabet enthielt einen Tippfehler ("...ABCDEFGE
        // HIJKLM...", also ein doppeltes 'E' und ein fehlendes 'G'); korrigiert auf das
        // vollständige Großbuchstaben-Alphabet. Fachlich unschädlich (die Menge möglicher
        // Passwörter ändert sich minimal), aber Parität zum Alt-Code
        // (common.Utilities#generatePassword, das das vollständige Alphabet nutzt).
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_!?=()#";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(alphabet.charAt(this.secureRandom.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
