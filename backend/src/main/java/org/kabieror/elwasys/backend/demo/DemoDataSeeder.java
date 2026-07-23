package org.kabieror.elwasys.backend.demo;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.kabieror.elwasys.backend.auth.PasswordVerificationService;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legt beim Start unter dem Profil {@code demo} einen realistischen, wiederverwendbaren
 * Demo-Datenbestand an, damit UI-Änderungen (Admin- und Benutzer-Portal, Dashboard) ohne
 * jedes Mal von Hand angelegte Daten visuell geprüft werden können (Auftrag: „für bessere
 * Tests und UI-Checks Demo-Daten anlegen"). Bewusst KEINE Flyway-Migration: Demo-Daten
 * gehören nicht ins Produktivschema (das würde jede echte Installation mit Beispielnutzern
 * verunreinigen) - stattdessen ein rein additiver Seeder, der ausschließlich unter dem
 * Profil {@code demo} aktiv ist (siehe {@code application-demo.yml} und
 * {@code backend/run-demo.sh}).
 *
 * <p>Der Seeder nutzt durchgängig die echten Repositories/Services (u.a.
 * {@link PasswordVerificationService} für Argon2id-Hashes, {@link CreditService} für
 * Buchungen, {@link PricingService} für die Preisberechnung der Verbrauchsbuchungen) - die
 * Demo-Daten durchlaufen damit exakt dieselben Wege wie produktive Daten und bleiben
 * konsistent (Guthabenstände passen zu den Buchungen, Preise zu Programm/Rabatt).
 *
 * <p><b>Idempotent</b>: erkennt an einem Marker-Benutzer ({@code anna}), ob bereits geseedet
 * wurde, und tut dann nichts - ein Neustart mit demselben Profil verdoppelt die Daten nicht.
 * Für einen frischen Bestand die Datenbank neu anlegen (siehe {@code backend/run-demo.sh},
 * das die Demo-DB bei jedem Lauf neu erzeugt).
 *
 * <p>Analog zu {@link org.kabieror.elwasys.backend.auth.AdminPasswordCliRunner} und
 * {@link org.kabieror.elwasys.backend.auth.terminal.TerminalTokenCliRunner} (gleiches
 * Muster: profilgebundener {@link ApplicationRunner}). Anders als jene läuft dieser aber im
 * NORMALEN Web-Kontext mit (das Portal soll ja hochfahren) - {@code application-demo.yml}
 * schließt daher Vaadin NICHT aus.
 */
@Component
@Profile("demo")
@Order(0)
public class DemoDataSeeder implements ApplicationRunner {

    /** Klartext-Passwort aller Demo-Benutzer (Login am Portal), siehe Klassen-Javadoc. */
    public static final String DEMO_PASSWORD = "demo";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserGroupRepository userGroupRepository;
    private final LocationRepository locationRepository;
    private final ProgramRepository programRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final ExecutionRepository executionRepository;
    private final CreditService creditService;
    private final PricingService pricingService;
    private final PasswordVerificationService passwordVerificationService;

    public DemoDataSeeder(UserGroupRepository userGroupRepository, LocationRepository locationRepository,
            ProgramRepository programRepository, DeviceRepository deviceRepository, UserRepository userRepository,
            ExecutionRepository executionRepository, CreditService creditService, PricingService pricingService,
            PasswordVerificationService passwordVerificationService) {
        this.userGroupRepository = userGroupRepository;
        this.locationRepository = locationRepository;
        this.programRepository = programRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.executionRepository = executionRepository;
        this.creditService = creditService;
        this.pricingService = pricingService;
        this.passwordVerificationService = passwordVerificationService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("anna").isPresent()) {
            log.info("[demo] Demo-Daten bereits vorhanden (Marker-Benutzer 'anna') - Seeding uebersprungen.");
            return;
        }
        // Issue #38 (Pre-Launch AP5): Schutz gegen einen versehentlichen Start des demo-Profils
        // gegen eine PRODUKTIV-DB (in der 'anna' natuerlich fehlt). Ohne diesen Waechter wuerde
        // der Seeder echte Daten mit Demo-Bestand vermengen und - schlimmer - das Admin-Passwort
        // auf "admin" ueberschreiben (siehe unten).
        //
        // Signal: Eine frische Installation hat das Admin-Passwort per V7 geleert (NULL); ein
        // betriebsbereites/produktives System hat es gesetzt (ueber den AdminPasswordCliRunner,
        // siehe Cutover-Runbook). Ist das Admin-Passwort gesetzt, der Demo-Marker 'anna' aber
        // nicht vorhanden, laeuft das demo-Profil offensichtlich gegen ein echtes System ->
        // Abbruch, bevor der Seeder genau dieses Passwort ueberschreibt. (Reine Zeilenzahlen
        // taugen als Signal nicht: der geteilte Integrationstest-Datenbestand ist nie leer.)
        boolean adminHasPassword = this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("admin")
                .map(UserEntity::getPassword)
                .filter(pw -> !pw.isBlank())
                .isPresent();
        if (adminHasPassword) {
            throw new IllegalStateException(
                    "[demo] Abbruch: Das Admin-Passwort ist bereits gesetzt, aber der Demo-Marker 'anna' fehlt. "
                            + "Das demo-Profil darf nicht gegen eine produktive Datenbank gestartet werden (es "
                            + "wuerde u.a. das Admin-Passwort auf 'admin' zuruecksetzen). Fuer Demo-Daten eine "
                            + "frische Datenbank verwenden (siehe backend/run-demo.sh).");
        }
        log.info("[demo] Lege Demo-Daten an ...");

        // --- Benutzergruppen (Default existiert bereits aus der Flyway-Baseline) --------------
        UserGroupEntity gDefault = findGroupByName("Default");
        UserGroupEntity gStudents = this.userGroupRepository.save(
                new UserGroupEntity("Studierende", DiscountType.FACTOR, 0.20)); // 20 % Rabatt
        UserGroupEntity gStaff = this.userGroupRepository.save(
                new UserGroupEntity("Hausmeister", DiscountType.FIX, 0.50)); // 0,50 EUR Nachlass
        UserGroupEntity gGuests = this.userGroupRepository.save(
                new UserGroupEntity("Gaeste", DiscountType.NONE, 0.0));

        // --- Standorte (Default existiert bereits) -------------------------------------------
        LocationEntity locDefault = this.locationRepository.findByName("Default")
                .orElseGet(() -> this.locationRepository.save(new LocationEntity("Default")));
        addGroups(locDefault.getValidUserGroups(), gDefault, gStudents, gStaff, gGuests);
        this.locationRepository.save(locDefault);

        LocationEntity locNorth = this.locationRepository.save(new LocationEntity("Waschkueche Nord"));
        addGroups(locNorth.getValidUserGroups(), gDefault, gStudents, gStaff);
        this.locationRepository.save(locNorth);

        LocationEntity locSouth = this.locationRepository.save(new LocationEntity("Waschkueche Sued"));
        locSouth.setOfflineMaxDurationMinutes(120);
        addGroups(locSouth.getValidUserGroups(), gDefault, gStudents, gGuests);
        this.locationRepository.save(locSouth);

        // --- Programme (Tarife) --------------------------------------------------------------
        ProgramEntity pCare = fixedProgram("Waschen 30 Grad Pflegeleicht", "1.50", gDefault, gStudents, gStaff,
                gGuests);
        ProgramEntity pColored = fixedProgram("Waschen 40 Grad Buntwaesche", "2.00", gDefault, gStudents, gStaff,
                gGuests);
        ProgramEntity pHot = fixedProgram("Waschen 60 Grad Kochwaesche", "2.50", gDefault, gStudents, gStaff);
        ProgramEntity pDelicate = fixedProgram("Feinwaesche 30 Grad", "1.80", gDefault, gStudents);
        ProgramEntity pDryer = dynamicProgram("Trocknen (dynamisch)", "0.50", "0.10", gDefault, gStudents, gStaff);

        // --- Geraete -------------------------------------------------------------------------
        DeviceEntity washNorth1 = fhemDevice("Waschmaschine Nord 1", 1, locNorth, "wm_nord_1",
                List.of(gDefault, gStudents, gStaff), List.of(pCare, pColored, pHot, pDelicate));
        deconzDevice("Waschmaschine Nord 2", 2, locNorth, "00:11:22:33:44:55:66:77-01",
                List.of(gDefault, gStudents), List.of(pColored, pHot));
        DeviceEntity dryerNorth1 = fhemDevice("Trockner Nord 1", 3, locNorth, "tr_nord_1",
                List.of(gDefault, gStudents, gStaff), List.of(pDryer));

        DeviceEntity washSouth1 = deconzDevice("Waschmaschine Sued 1", 1, locSouth, "00:11:22:33:44:55:66:88-01",
                List.of(gDefault, gStudents, gGuests), List.of(pCare, pColored, pHot));
        deconzDevice("Trockner Sued 1", 2, locSouth, "00:11:22:33:44:55:66:88-02",
                List.of(gDefault, gStudents), List.of(pDryer));
        DeviceEntity washSouth2 = fhemDevice("Waschmaschine Sued 2", 3, locSouth, "wm_sued_2",
                List.of(gDefault), List.of(pCare, pColored));
        washSouth2.setEnabled(false); // ausser Betrieb - zeigt den "deaktiviert"-Zustand im Portal
        this.deviceRepository.save(washSouth2);

        // --- Benutzer ------------------------------------------------------------------------
        // Der aus der Flyway-Baseline vorhandene Admin bekommt ein bekanntes Passwort (admin),
        // damit man sich am Demo-Portal sofort als Admin anmelden kann.
        this.userRepository.findByUsernameIgnoreCaseAndDeletedFalse("admin").ifPresent(admin -> {
            admin.setEmail("admin@example.org");
            admin.setPassword(this.passwordVerificationService.encodeNew("admin"));
            this.userRepository.save(admin);
        });

        UserEntity anna = user("Anna Beispiel", "anna", gStudents, "anna@example.org", "0004001001");
        UserEntity ben = user("Ben Muster", "ben", gStudents, "ben@example.org", "0004001002");
        UserEntity clara = user("Clara Wartung", "clara", gStaff, "clara@example.org", "0004001003");
        UserEntity david = user("David Gast", "david", gGuests, "david@example.org", "0004001004");
        david.setBlocked(true); // gesperrter Benutzer - zeigt den "blockiert"-Zustand im Portal
        this.userRepository.save(david);
        UserEntity eva = user("Eva Meldung", "eva", gDefault, "eva@example.org", "0004001005");
        eva.setPushNotification(true);
        eva.setPushoverUserKey("uQiRzpo4DXghDmr9QzzfQu27cmVRsG");
        this.userRepository.save(eva);

        // --- Guthaben-Aufladungen ------------------------------------------------------------
        this.creditService.inpayment(anna, new BigDecimal("25.00"), "Aufladung (Demo)");
        this.creditService.inpayment(ben, new BigDecimal("10.00"), "Aufladung (Demo)");
        this.creditService.inpayment(clara, new BigDecimal("50.00"), "Aufladung (Demo)");
        this.creditService.inpayment(david, new BigDecimal("5.00"), "Aufladung (Demo)");
        this.creditService.inpayment(eva, new BigDecimal("15.00"), "Aufladung (Demo)");

        // --- Ausfuehrungshistorie (abgeschlossene Waschvorgaenge) ----------------------------
        LocalDateTime now = LocalDateTime.now();
        finishedExecution(washNorth1, pColored, anna, now.minusDays(3).withHour(9), Duration.ofMinutes(95));
        finishedExecution(washNorth1, pCare, ben, now.minusDays(2).withHour(18), Duration.ofMinutes(70));
        finishedExecution(washSouth1, pHot, clara, now.minusDays(1).withHour(14), Duration.ofMinutes(120));
        finishedExecution(dryerNorth1, pDryer, anna, now.minusDays(1).withHour(20), Duration.ofMinutes(60));

        // --- Laufende Ausfuehrungen (Dashboard zeigt "Besetzt" inkl. Restzeit) ---------------
        runningExecution(washSouth1, pColored, anna, now.minusMinutes(25));
        runningExecution(dryerNorth1, pDryer, ben, now.minusMinutes(10));

        log.info("[demo] Demo-Daten angelegt: 4 Gruppen, 3 Standorte, 5 Programme, 6 Geraete, "
                + "5 Benutzer (+admin), Guthaben, Historie und laufende Ausfuehrungen. "
                + "Login am Portal: admin/admin bzw. <benutzer>/{}", DEMO_PASSWORD);
    }

    // ----------------------------------------------------------------------------------------
    // Hilfsmethoden
    // ----------------------------------------------------------------------------------------

    private UserGroupEntity findGroupByName(String name) {
        return this.userGroupRepository.findAllByOrderByNameAsc().stream()
                .filter(g -> name.equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Erwartete Basis-Benutzergruppe '" + name + "' nicht gefunden (Flyway-Baseline?)"));
    }

    private static void addGroups(java.util.Set<UserGroupEntity> target, UserGroupEntity... groups) {
        for (UserGroupEntity g : groups) {
            target.add(g);
        }
    }

    private ProgramEntity fixedProgram(String name, String flagfall, UserGroupEntity... validGroups) {
        ProgramEntity p = new ProgramEntity(name, ProgramType.FIXED, 7200);
        p.setFreeDurationSeconds(120);
        p.setFlagfall(new BigDecimal(flagfall));
        p.setRate(BigDecimal.ZERO);
        p.setTimeUnit(TimeUnitType.MINUTES);
        p.setAutoEnd(true);
        p.setEarliestAutoEndSeconds(600);
        addGroups(p.getValidUserGroups(), validGroups);
        return this.programRepository.save(p);
    }

    private ProgramEntity dynamicProgram(String name, String flagfall, String rate, UserGroupEntity... validGroups) {
        ProgramEntity p = new ProgramEntity(name, ProgramType.DYNAMIC, 5400);
        p.setFreeDurationSeconds(60);
        p.setFlagfall(new BigDecimal(flagfall));
        p.setRate(new BigDecimal(rate));
        p.setTimeUnit(TimeUnitType.MINUTES);
        p.setAutoEnd(true);
        p.setEarliestAutoEndSeconds(300);
        addGroups(p.getValidUserGroups(), validGroups);
        return this.programRepository.save(p);
    }

    private DeviceEntity fhemDevice(String name, int position, LocationEntity location, String fhemName,
            List<UserGroupEntity> validGroups, List<ProgramEntity> programs) {
        DeviceEntity d = new DeviceEntity(name, position, location);
        d.setFhemName(fhemName);
        d.setFhemSwitchName(fhemName + "_sw");
        d.setFhemPowerName(fhemName + "_pw");
        d.getValidUserGroups().addAll(validGroups);
        d.getPrograms().addAll(programs);
        return this.deviceRepository.save(d);
    }

    private DeviceEntity deconzDevice(String name, int position, LocationEntity location, String deconzUuid,
            List<UserGroupEntity> validGroups, List<ProgramEntity> programs) {
        DeviceEntity d = new DeviceEntity(name, position, location);
        d.setDeconzUuid(deconzUuid);
        d.getValidUserGroups().addAll(validGroups);
        d.getPrograms().addAll(programs);
        return this.deviceRepository.save(d);
    }

    private UserEntity user(String name, String username, UserGroupEntity group, String email, String cardId) {
        UserEntity u = new UserEntity(name, username, group);
        u.setEmail(email);
        u.setCardIds(new String[] {cardId});
        u.setPassword(this.passwordVerificationService.encodeNew(DEMO_PASSWORD));
        return this.userRepository.save(u);
    }

    /**
     * Legt einen abgeschlossenen Waschvorgang mit fester Start-/Stoppzeit an und bucht - wie
     * im echten Ablauf ({@code ExecutionService#finishExecution} -&gt;
     * {@code CreditService#payExecution}) - den Preis vom Guthaben des Benutzers ab, damit
     * Guthabenstand und Buchungshistorie im Portal konsistent zur Historie sind.
     */
    private void finishedExecution(DeviceEntity device, ProgramEntity program, UserEntity user,
            LocalDateTime start, Duration duration) {
        ExecutionEntity e = new ExecutionEntity(device, program, user);
        e.setStart(start);
        e.setStop(start.plus(duration));
        e.setFinished(true);
        ExecutionEntity saved = this.executionRepository.save(e);
        BigDecimal price = this.pricingService.getPrice(program, duration, user);
        if (price != null) {
            this.creditService.payExecution(saved, price);
        }
    }

    /** Legt eine noch laufende Ausfuehrung an (Start gesetzt, kein Stop, nicht abgeschlossen). */
    private void runningExecution(DeviceEntity device, ProgramEntity program, UserEntity user, LocalDateTime start) {
        ExecutionEntity e = new ExecutionEntity(device, program, user);
        e.setStart(start);
        e.setFinished(false);
        this.executionRepository.save(e);
    }
}
