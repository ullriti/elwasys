package org.kabieror.elwasys.backend.service;

import org.kabieror.elwasys.backend.auth.PasswordVerificationService;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService.VerificationResult;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.exception.InvalidCurrentPasswordException;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ändern des eigenen Passworts durch einen angemeldeten Benutzer (Phase 3 AP4, siehe
 * kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../components/ChangePasswordWindow} (Alt-Portal, Testfall P16).
 *
 * <p>1:1 wie im Alt-Fenster wird zuerst das aktuelle Passwort geprüft
 * ({@code OldPasswordValidator}, dort {@code User#checkPassword}) - hier über
 * {@link PasswordVerificationService#verify}, das (anders als der Alt-Code) sowohl
 * Argon2id- als auch SHA1-Bestandshashes akzeptiert (ein Benutzer, dessen Hash noch nicht
 * migriert wurde, kann sein Passwort also weiterhin über das aktuelle SHA1-Passwort ändern).
 * Das NEU gesetzte Passwort wird immer im neuen Argon2id-Format gespeichert (siehe
 * {@link PasswordVerificationService#encodeNew}, "jedes Neusetzen eines Passworts über das
 * Backend soll das neue Format erzeugen" - Auftrag AP3, kb/05-migration-plan.md
 * "Entscheidungen"). <b>Wichtig für den Parallelbetrieb (P16, "erneuter Login mit neuem
 * Passwort klappt")</b>: das Alt-Portal ({@code common.User#checkPassword}) vergleicht ein
 * eingegebenes Passwort per {@code this.password.equals(Utilities.sha1(password))} GEGEN DEN
 * GESPEICHERTEN WERT - unabhängig vom Format des gespeicherten Hashs prüft es also praktisch
 * "sieht der SHA1 des eingegebenen Passworts wie der gespeicherte String aus". Ein neu über
 * DIESES Backend gesetztes Argon2id-Passwort kann sich damit NICHT mehr über das Alt-Portal
 * anmelden (der SHA1 des neuen Klartexts stimmt nie mit einem Argon2id-String überein) - das
 * ist eine bewusste, hier dokumentierte Einschränkung: ein Nutzer, der sein Passwort über das
 * NEUE Portal ändert, muss sich künftig auch dort anmelden. Das steht nicht im Widerspruch zur
 * Rahmenbedingung "Nutzer dürfen sich nicht umstellen müssen" - jene bezieht sich auf
 * UNVERÄNDERTES Verhalten für Nutzer, die (noch) nichts am neuen Portal tun; wer aktiv ein
 * neues Passwort über das neue Portal setzt, hat sich bereits für den neuen Login-Weg
 * entschieden. Für P16 (Portal-interner Test) ist das unschädlich, weil dort NUR gegen das
 * neue Backend erneut eingeloggt wird.
 */
@Service
public class PasswordService {

    private final UserRepository userRepository;

    private final PasswordVerificationService passwordVerificationService;

    public PasswordService(UserRepository userRepository, PasswordVerificationService passwordVerificationService) {
        this.userRepository = userRepository;
        this.passwordVerificationService = passwordVerificationService;
    }

    /**
     * Ändert das Passwort eines angemeldeten Benutzers, nachdem das aktuelle Passwort geprüft
     * wurde.
     *
     * @throws InvalidCurrentPasswordException wenn {@code currentPassword} nicht zum
     *                                          gespeicherten Hash passt
     */
    @Transactional
    public void changeOwnPassword(UserEntity user, String currentPassword, String newPassword) {
        VerificationResult result = this.passwordVerificationService.verify(currentPassword, user.getPassword());
        if (!result.matches()) {
            throw new InvalidCurrentPasswordException();
        }
        setNewPassword(user, newPassword);
    }

    /**
     * Setzt ein neues Passwort ohne Prüfung des alten - für den admin-seitigen Passwort-Reset
     * ({@code UserFormDialog}, Nachfolger des {@code UserWindow}-"Sende dem Benutzer per Email
     * ein neues Passwort"-Teils) sowie den öffentlichen Link-Reset
     * ({@code PasswordResetService#resetPassword}). 1:1-Portierung von
     * {@code common.User#changePassword}: ein evtl. noch offener Reset-Schlüssel wird
     * zusätzlich konsumiert (Einmalverwendung), auch wenn das Passwort auf einem anderen Weg
     * (z.B. hier über den Admin-Dialog statt über den Link) geändert wurde - 1:1 wie im
     * Alt-Code, der das ausnahmslos in {@code changePassword} erledigt.
     */
    @Transactional
    public void setNewPassword(UserEntity user, String newPassword) {
        user.setPassword(this.passwordVerificationService.encodeNew(newPassword));
        user.setPasswordResetKey(null);
        user.setPasswordResetTimeout(null);
        this.userRepository.save(user);
    }
}
