package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.CreditAccountingEntryRepository;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Regressionstest zum N+1-Befund aus dem Cutover vom 2026-09-20.
 *
 * <p><b>Was schiefging:</b> {@code CreditAccountingEntryEntity.execution} war
 * {@code @ManyToOne(fetch = EAGER)}, die abgeleitete Abfrage
 * {@code findByUser_IdOrderByDateDescIdDesc} erzeugt aber nur ein einfaches SELECT ohne JOIN.
 * Hibernate lud deshalb fuer JEDE Ergebniszeile eine eigene Einzelabfrage nach
 * ({@code EntitySelectFetchInitializer -> SingleIdLoadPlan.load}). Auf echten Bestandsdaten
 * (ein Konto mit 506 Buchungen) brauchte das Benutzer-Dashboard dadurch rund 20 Minuten - und
 * jedes Domain-Event stiess ueber den {@code UiBroadcaster} eine weitere solche Abfrage an.
 *
 * <p><b>Zwei Fallen, die dieser Test bewusst vermeidet:</b>
 * <ol>
 *   <li><b>Er zaehlt Statements, nicht Millisekunden.</b> Eine Laufzeitschranke waere mit
 *       wenigen Testzeilen auf einem schnellen Rechner auch bei N+1 gruen geblieben - der
 *       Fehler skaliert ja mit der Zeilenzahl.</li>
 *   <li><b>Die Buchungen verweisen auf ECHTE Ausfuehrungen.</b> Der bestehende
 *       {@code CreditServiceAccountingHistoryTest} legt sie mit {@code execution = null} an;
 *       bei {@code null} gibt es nichts nachzuladen, und der Test bliebe auch mit dem
 *       zurueckgedrehten {@code EAGER} gruen.</li>
 * </ol>
 *
 * <p><b>Gegenprobe:</b> Mit {@code fetch = EAGER} schlaegt dieser Test fehl - dann stehen
 * {@value #BOOKINGS} zusaetzliche Statements in der Statistik.
 */
class CreditServiceAccountingHistoryFetchTest extends AbstractBackendIT {

    /** Genug Zeilen, damit N+1 eindeutig von einer konstanten Anzahl unterscheidbar ist. */
    private static final int BOOKINGS = 10;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private CreditAccountingEntryRepository creditAccountingEntryRepository;

    @Autowired
    private CreditService creditService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Die Buchungshistorie kostet eine feste Anzahl Abfragen, unabhaengig von der Zeilenzahl")
    void accountingHistoryDoesNotIssueOneQueryPerRow() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = this.userRepository.save(
                new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
        ProgramEntity program = this.programRepository.save(
                new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 3600));

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 12, 0);
        for (int i = 0; i < BOOKINGS; i++) {
            // Jede Buchung mit EIGENER Ausfuehrung - nur so entsteht bei EAGER wirklich
            // eine Einzelabfrage pro Zeile (gleiche Ausfuehrung waere nach der ersten im
            // Persistenzkontext und wuerde den Fehler verdecken).
            ExecutionEntity execution = this.executionRepository.save(
                    new ExecutionEntity(device, program, user));
            this.creditAccountingEntryRepository.save(new CreditAccountingEntryEntity(
                    user, execution, new BigDecimal("1.00"), base.plusMinutes(i), "Test " + i));
        }

        Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var entries = this.creditService.getAccountingEntries(user);

        assertThat(entries).hasSize(BOOKINGS);
        // Erwartet wird EINE Abfrage; die Schranke laesst etwas Luft (Hibernate darf z.B. den
        // Benutzer separat aufloesen), liegt aber deutlich unter BOOKINGS.
        assertThat(statistics.getPrepareStatementCount())
                .as("Statements fuer %d Buchungen - bei N+1 waeren es mindestens %d", BOOKINGS, BOOKINGS + 1)
                .isLessThanOrEqualTo(3L);
    }
}
