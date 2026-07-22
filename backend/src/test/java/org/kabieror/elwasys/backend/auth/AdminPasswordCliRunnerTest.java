package org.kabieror.elwasys.backend.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.PasswordService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Reiner Unit-Test (Mockito, keine DB) für {@link AdminPasswordCliRunner} (Phase 5 AP2, siehe
 * kb/05-migration-plan.md). Prüft die Argument-Validierung sowie, dass das Setzen des
 * Passworts an {@link PasswordService#setNewPassword} delegiert wird - genau derselbe Weg, den
 * auch der admin-seitige Passwort-Reset im Portal verwendet (siehe {@link PasswordService}
 * Javadoc), sodass hier bewusst KEIN eigenes Hashing getestet wird (das deckt
 * {@code PasswordServiceTest}/{@code PasswordVerificationServiceTest} bereits ab).
 */
@ExtendWith(MockitoExtension.class)
class AdminPasswordCliRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Test
    void setsThePasswordOfAnExistingUserViaPasswordService() {
        UserGroupEntity group = new UserGroupEntity("Default", DiscountType.NONE, 0);
        UserEntity admin = new UserEntity("Administrator", "admin", group);
        when(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("admin")).thenReturn(Optional.of(admin));

        AdminPasswordCliRunner runner = new AdminPasswordCliRunner(this.userRepository, this.passwordService);
        runner.run(new DefaultApplicationArguments("--username=admin", "--password=new-secret"));

        verify(this.passwordService).setNewPassword(eq(admin), eq("new-secret"));
    }

    @Test
    void failsClearlyForAnUnknownUsername() {
        when(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("ghost")).thenReturn(Optional.empty());

        AdminPasswordCliRunner runner = new AdminPasswordCliRunner(this.userRepository, this.passwordService);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--username=ghost", "--password=x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ghost");
        verify(this.passwordService, never()).setNewPassword(any(), any());
    }

    @Test
    void failsClearlyWhenArgumentsAreMissing() {
        AdminPasswordCliRunner runner = new AdminPasswordCliRunner(this.userRepository, this.passwordService);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--username=admin")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--password=x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments())).isInstanceOf(
                IllegalArgumentException.class);
    }
}
