package org.kabieror.elwasys.backend.demo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.PricingService;

/**
 * Issue #38 (Pre-Launch AP5): der {@link DemoDataSeeder} darf gegen eine bereits benutzte
 * (produktive) Datenbank NICHT seeden - sonst vermengte er echte Daten mit Demo-Bestand und
 * setzte u.a. das Admin-Passwort auf "admin". Reiner Unit-Test mit Mock-Repositories (der
 * Waechter greift vor jedem Schreibzugriff, daher werden die uebrigen Repositories nicht
 * angefasst); ein Integrationstest waere hier ungeeignet, weil die gemeinsame IT-Datenbank
 * unter dem demo-Profil ohnehin bereits geseedet ist (siehe {@code DemoDataSeederTest}).
 */
class DemoDataSeederGuardTest {

    private final UserGroupRepository userGroupRepository = mock(UserGroupRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final ProgramRepository programRepository = mock(ProgramRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final CreditService creditService = mock(CreditService.class);
    private final PricingService pricingService = mock(PricingService.class);
    private final PasswordVerificationService passwordVerificationService = mock(PasswordVerificationService.class);

    private DemoDataSeeder newSeeder() {
        return new DemoDataSeeder(this.userGroupRepository, this.locationRepository, this.programRepository,
                this.deviceRepository, this.userRepository, this.executionRepository, this.creditService,
                this.pricingService, this.passwordVerificationService);
    }

    @Test
    void abortsWhenAdminPasswordIsSetButNoDemoMarker() {
        // Marker 'anna' fehlt, aber der Admin hat bereits ein Passwort -> produktive DB.
        when(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna")).thenReturn(Optional.empty());
        UserEntity admin = mock(UserEntity.class);
        when(admin.getPassword()).thenReturn("$argon2id$v=19$m=...real-hash...");
        when(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> newSeeder().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("produktive");
    }

    @Test
    void skipsSilentlyWhenDemoMarkerAlreadyPresent() {
        // Marker 'anna' vorhanden -> bereits geseedet, der Seeder tut nichts (kein Abbruch, kein
        // erneutes Schreiben). Der Waechter darf hier NICHT anschlagen (admin-Lookup gar nicht
        // erst nötig).
        lenient().when(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("admin"))
                .thenReturn(Optional.empty());
        when(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna"))
                .thenReturn(Optional.of(mock(UserEntity.class)));

        newSeeder().run(null); // wirft nicht
    }
}
