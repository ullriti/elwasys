package org.kabieror.elwasys.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.DashboardService;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/**
 * Charakterisiert den {@link DemoDataSeeder}: unter dem Profil {@code demo} legt der Seeder
 * beim Kontextstart einen zusammenhaengenden Beispielbestand an (Benutzer, Gruppen, Geraete,
 * Programme, Guthaben, Historie, laufende Ausfuehrungen) und ist idempotent (ein erneuter
 * Lauf verdoppelt nichts). Getestet werden bewusst Demo-spezifische Marker statt globaler
 * Zeilenzahlen, weil die AbstractBackendIT-Suite eine gemeinsame Datenbank nutzt (siehe dort).
 */
@ActiveProfiles("demo")
class DemoDataSeederTest extends AbstractBackendIT {

    @Autowired
    private DemoDataSeeder demoDataSeeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private CreditService creditService;

    @Autowired
    private DashboardService dashboardService;

    @Test
    void seedsMarkerUserWithGroupAndPositiveCredit() {
        UserEntity anna = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna").orElseThrow();
        assertThat(anna.getGroup().getName()).isEqualTo("Studierende");
        // Aufladung 25,00 minus abgebuchte Historie -> weiterhin positives Guthaben.
        assertThat(this.creditService.getCredit(anna)).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void seedsBlockedGuestUser() {
        UserEntity david = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("david").orElseThrow();
        assertThat(david.isBlocked()).isTrue();
        assertThat(david.getGroup().getName()).isEqualTo("Gaeste");
    }

    @Test
    void seedsDisabledDevice() {
        DeviceEntity disabled = this.deviceRepository.findAll().stream()
                .filter(d -> "Waschmaschine Sued 2".equals(d.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void dashboardShowsAtLeastOneOccupiedDevice() {
        boolean anyOccupied = this.dashboardService.getLocationStatuses().stream()
                .flatMap(loc -> loc.devices().stream())
                .anyMatch(DashboardService.DeviceStatus::isOccupied);
        assertThat(anyOccupied).isTrue();
    }

    @Test
    void secondRunIsIdempotent() {
        UserEntity anna = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna").orElseThrow();
        int accountingBefore = this.creditService.getAccountingEntries(anna).size();

        // Erneuter Lauf gegen die bereits geseedete DB darf nichts doppelt anlegen.
        this.demoDataSeeder.run(null);

        assertThat(this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna")).isPresent();
        UserEntity annaAfter = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna").orElseThrow();
        assertThat(this.creditService.getAccountingEntries(annaAfter)).hasSize(accountingBefore);
    }
}
