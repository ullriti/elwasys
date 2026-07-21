package org.kabieror.elwasys.backend.auth;

import java.util.List;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.PasswordService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Minimaler, dokumentierter Weg zum (Neu-)Setzen des Passworts eines bestehenden Benutzers
 * (insbesondere des Administrators) in Phase 5 (siehe kb/05-migration-plan.md, AP2: "DB
 * härten" - der Default-Admin-Seed mit fest eingebettetem Passwort entfällt für frische
 * Installationen, siehe {@code V7__remove_default_admin_password.sql}). Analog zu
 * {@link org.kabieror.elwasys.backend.auth.terminal.TerminalTokenCliRunner} (gleiches Muster:
 * einmaliger CLI-Aufruf statt Admin-UI, siehe dessen Javadoc für die ausführlichere
 * Begründung). Läuft NUR unter dem Profil {@code admin-cli} (siehe
 * {@code application-admin-cli.yml}, das wie {@code application-token-cli.yml} zusätzlich
 * {@code spring.main.web-application-type: none} setzt und Vaadins Autokonfiguration
 * ausschließt, damit dieser einmalige CLI-Aufruf keinen Webserver hochfährt und der Prozess
 * nach Abschluss von selbst beendet).
 *
 * <p>Setzt das Passwort über {@link PasswordService#setNewPassword} - denselben Weg, den auch
 * der admin-seitige Passwort-Reset im Portal (UserFormDialog) und der öffentliche Link-Reset
 * (PasswordResetService) verwenden. Das Passwort wird dadurch IMMER im neuen Argon2id-Format
 * gespeichert ({@link PasswordVerificationService#encodeNew}) - kein separates/neues
 * Hashing-Schema.
 *
 * <p><b>Setzen</b> (siehe kb/04-build-and-run.md für das vollständige Kommando):
 * <pre>
 * java -jar backend/target/elwasys-backend.jar \
 *     --spring.profiles.active=admin-cli \
 *     --username=admin \
 *     --password=&lt;neuesPasswort&gt;
 * </pre>
 * Das Klartext-Passwort wird NIE ausgegeben (weder auf stdout noch geloggt) - nur eine
 * Bestätigung, für welchen Benutzer das Passwort gesetzt wurde.
 */
@Component
@Profile("admin-cli")
public class AdminPasswordCliRunner implements ApplicationRunner {

    private final UserRepository userRepository;

    private final PasswordService passwordService;

    public AdminPasswordCliRunner(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> usernames = args.getOptionValues("username");
        List<String> passwords = args.getOptionValues("password");
        if (usernames == null || usernames.isEmpty() || passwords == null || passwords.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: --spring.profiles.active=admin-cli --username=<Benutzername> --password=<neuesPasswort>");
        }
        String username = usernames.get(0);
        String password = passwords.get(0);

        setPassword(username, password);
    }

    private void setPassword(String username, String password) {
        UserEntity user = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse(username)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user '" + username + "'."));
        // PasswordService#setNewPassword ist selbst @Transactional - hashformatiert immer neu
        // im Argon2id-Format (siehe Klassen-Javadoc), kein separates Hashing-Schema hier.
        this.passwordService.setNewPassword(user, password);

        System.out.println("================================================================");
        System.out.println("Passwort gesetzt.");
        System.out.println("  Benutzer: " + user.getUsername() + " (id=" + user.getId() + ")");
        System.out.println("================================================================");
    }
}
