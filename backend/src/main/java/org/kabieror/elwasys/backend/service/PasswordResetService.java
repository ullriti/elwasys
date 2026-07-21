package org.kabieror.elwasys.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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
 * Passwort-Reset per Email (Phase 3 AP4, siehe kb/05-migration-plan.md, kb/03-modules.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/PasswordForgotWindow} +
 * {@code ResetPasswordWindow} (Selbstbedienung, Testfall P19) sowie des
 * "Sende dem Benutzer per Email ein neues Passwort"-Teils von
 * {@code Portal/.../components/UserWindow} (Admin-Aktion).
 *
 * <p><b>Speicherung des Schlüssels</b>: nutzt bewusst die BESTEHENDEN Spalten
 * {@code users.password_reset_key}/{@code users.password_reset_timeout} (Teil der
 * Flyway-Baseline {@code V1__baseline_schema_0_4_0.sql}, 1:1 aus dem Alt-Schema übernommen,
 * siehe kb/02-data-model.md) statt einer neuen Migration - diese Spalten existieren im
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

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordService passwordService,
            NotificationService notificationService, PasswordResetProperties properties) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    /**
     * 1:1-Portierung von {@code PasswordForgotWindow#execute}: sucht den Benutzer über seine
     * Email-Adresse, erzeugt bei Erfolg einen neuen Reset-Schlüssel und verschickt eine Email
     * mit dem Reset-Link. Bewusst dieselbe (aus Sicht der Informationspreisgabe nicht ideale,
     * aber 1:1 vom Alt-Code übernommene) Rückmeldung: existiert die Email-Adresse nicht, wirft
     * diese Methode {@link UserNotFoundForEmailException} statt stillschweigend nichts zu tun -
     * der Aufrufer (Dialog) zeigt dieselbe Fehlermeldung wie das Alt-{@code PasswordForgotWindow}
     * ("Es konnte kein Benutzer mit der angegebenen Email-Adresse gefunden werden.").
     */
    @Transactional
    public void requestReset(String email) {
        UserEntity user = this.userRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseThrow(UserNotFoundForEmailException::new);

        String token = generateToken();
        user.setPasswordResetKey(token);
        user.setPasswordResetTimeout(LocalDateTime.now().plus(this.properties.getTokenValidity()));
        this.userRepository.save(user);

        String resetUrl = this.properties.getPortalBaseUrl() + "/reset-password?key=" + token;
        this.notificationService.sendPasswordResetEmail(user, resetUrl);
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
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGEHIJKLMNOPQRSTUVWXYZ0123456789-_!?=()#";
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(alphabet.charAt(this.secureRandom.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Entspricht dem "Email unbekannt"-Fehlerfall in {@code PasswordForgotWindow#execute}.
     */
    public static final class UserNotFoundForEmailException extends RuntimeException {
        public UserNotFoundForEmailException() {
            super("Es konnte kein Benutzer mit der angegebenen Email-Adresse gefunden werden.");
        }
    }
}
