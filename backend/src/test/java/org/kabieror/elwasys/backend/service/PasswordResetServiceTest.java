package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.InvalidOrExpiredResetTokenException;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.PasswordResetService.UserNotFoundForEmailException;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Tests für {@link PasswordResetService} (Phase 3 AP4, Testfall P19, siehe
 * docs/kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../components/PasswordForgotWindow}/{@code ResetPasswordWindow}/dem
 * Admin-Passwort-Reset-Teil von {@code UserWindow}. Läuft mit echtem SMTP-Mock (GreenMail,
 * siehe {@code spring.mail.*}-Override unten) durch den vollen Spring-Kontext, damit auch der
 * tatsächliche Mailversand (nicht nur die Schlüssel-Logik) mitgetestet ist.
 */
class PasswordResetServiceTest extends AbstractBackendIT {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
        // GreenMails Test-SMTP-Server verlangt (anders als die application.yml-Defaults
        // mail.smtp.auth=true/mail.smtp.ssl.enable=true) weder Authentifizierung noch TLS -
        // sonst schlägt jeder Sendeversuch fehl, siehe NotificationServiceEmailTest (dort
        // umgangen, indem ein eigener JavaMailSenderImpl statt der Spring-Autokonfiguration
        // verwendet wird; dieser Test nutzt bewusst den vollen Spring-Kontext/-Bean).
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.ssl.enable", () -> "false");
        registry.add("elwasys.notifications.smtp.sender-address", () -> "elwasys@example.com");
    }

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private PasswordVerificationService passwordVerificationService;

    private UserEntity newUserWithEmail(String email) {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group);
        user.setEmail(email);
        return this.userRepository.save(user);
    }

    @Test
    void requestResetGeneratesATokenAndSendsAnEmailWithTheResetLink() throws Exception {
        String email = Fixtures.unique("user") + "@example.com";
        UserEntity user = newUserWithEmail(email);

        this.passwordResetService.requestReset(email);

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPasswordResetKey()).isNotBlank();
        assertThat(reloaded.getPasswordResetTimeout()).isAfter(LocalDateTime.now());

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Passwort zurücksetzen");
        assertThat(received[0].getAllRecipients()).extracting(Object::toString).containsExactly(email);
        String body = ((String) received[0].getContent()).replace("\r\n", "\n");
        assertThat(body).contains("/reset-password?key=" + reloaded.getPasswordResetKey());
    }

    @Test
    void requestResetForAnUnknownEmailThrowsWithoutSendingAnyMail() {
        assertThatThrownBy(() -> this.passwordResetService.requestReset("nobody-" + Fixtures.unique("x")
                + "@example.com")).isInstanceOf(UserNotFoundForEmailException.class);

        assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void resetPasswordWithAValidTokenSetsTheNewPasswordAndConsumesTheToken() {
        String email = Fixtures.unique("user") + "@example.com";
        UserEntity user = newUserWithEmail(email);
        this.passwordResetService.requestReset(email);
        String token = this.userRepository.findById(user.getId()).orElseThrow().getPasswordResetKey();

        assertThat(this.passwordResetService.isValidToken(token)).isTrue();

        this.passwordResetService.resetPassword(token, "brand-new-secret");

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        assertThat(this.passwordVerificationService.verify("brand-new-secret", reloaded.getPassword()).matches())
                .isTrue();
        assertThat(reloaded.getPasswordResetKey()).as("the token is single-use").isNull();
        assertThat(this.passwordResetService.isValidToken(token)).isFalse();
    }

    @Test
    void resetPasswordWithAnUnknownTokenFails() {
        assertThatThrownBy(() -> this.passwordResetService.resetPassword("not-a-real-token", "whatever")).isInstanceOf(
                InvalidOrExpiredResetTokenException.class);
    }

    @Test
    void resetPasswordWithAnExpiredTokenFails() {
        String email = Fixtures.unique("user") + "@example.com";
        UserEntity user = newUserWithEmail(email);
        user.setPasswordResetKey("expired-token-" + Fixtures.unique("t"));
        user.setPasswordResetTimeout(LocalDateTime.now().minusMinutes(1));
        user = this.userRepository.save(user);
        String token = user.getPasswordResetKey();

        assertThat(this.passwordResetService.isValidToken(token)).isFalse();
        assertThatThrownBy(() -> this.passwordResetService.resetPassword(token, "whatever")).isInstanceOf(
                InvalidOrExpiredResetTokenException.class);
    }

    @Test
    void resetPasswordByAdminAndNotifySetsARandomPasswordAndSendsIt() throws Exception {
        String email = Fixtures.unique("user") + "@example.com";
        UserEntity user = newUserWithEmail(email);

        this.passwordResetService.resetPasswordByAdminAndNotify(user);

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Waschportal - Neues Passwort");

        UserEntity reloaded = this.userRepository.findById(user.getId()).orElseThrow();
        String body = ((String) received[0].getContent()).replace("\r\n", "\n");
        // Die im Mail-Text verschickte Zeichenkette ("hier ist dein neues Passwort für das
        // Waschportal: <pw>") muss tatsächlich zum neu gespeicherten Hash passen.
        String marker = "Waschportal: ";
        int start = body.indexOf(marker) + marker.length();
        int end = body.indexOf('\n', start);
        String sentPassword = body.substring(start, end);
        assertThat(this.passwordVerificationService.verify(sentPassword, reloaded.getPassword()).matches()).isTrue();
    }
}
