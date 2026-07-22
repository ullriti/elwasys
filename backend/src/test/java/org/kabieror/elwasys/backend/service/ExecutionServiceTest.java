package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
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
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Charakterisierungstests für die Execution-Lebenszyklus-Logik auf Persistenzebene (Start/
 * Ende/Abbruch/Reset, abgelaufene Ausführungen) - 1:1 portiert aus
 * {@code org.kabieror.elwasys.common.Execution} und den Datenbank-Anteilen von
 * {@code DataManager}/{@code ExecutionManager}/{@code ExecutionFinisher} im Client, siehe
 * docs/kb/05-migration-plan.md, AP2.
 */
class ExecutionServiceTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ExecutionRepository executionRepository;
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private CreditService creditService;

    private UserEntity newUser() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        return this.userRepository.save(new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
    }

    private DeviceEntity newDevice() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        return this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
    }

    private ProgramEntity newProgram(int maxDurationSeconds) {
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, maxDurationSeconds);
        program.setFlagfall(new BigDecimal("3.00"));
        program.setFreeDurationSeconds(0);
        return this.programRepository.save(program);
    }

    /**
     * DYNAMIC-Programm mit Grundgebühr 1.00 und Zeitpreis 2.00/Stunde - für die
     * Preis-Deckel-Tests (Issue #18): nur bei einem DYNAMIC-Programm hängt der Preis überhaupt
     * von der Dauer ab (FIXED = Grundgebühr unabhängig von der Dauer).
     */
    private ProgramEntity newDynamicHourlyProgram(int maxDurationSeconds) {
        ProgramEntity program = new ProgramEntity(Fixtures.unique("dyn"), ProgramType.DYNAMIC, maxDurationSeconds);
        program.setFlagfall(new BigDecimal("1.00"));
        program.setRate(new BigDecimal("2.00"));
        program.setTimeUnit(TimeUnitType.HOURS);
        program.setFreeDurationSeconds(0);
        return this.programRepository.save(program);
    }

    @Test
    void createExecutionStartsUnstartedAndUnfinished() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);

        ExecutionEntity execution = this.executionService.createExecution(device, program, user);

        assertThat(execution.getId()).isNotNull();
        assertThat(execution.getStart()).isNull();
        assertThat(execution.getStop()).isNull();
        assertThat(execution.isFinished()).isFalse();
    }

    @Test
    void startExecutionSetsStartTimeOnlyOnce() throws InterruptedException {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        ExecutionEntity execution = this.executionService.createExecution(device, program, user);

        execution = this.executionService.startExecution(execution);
        LocalDateTime firstStart = execution.getStart();
        assertThat(firstStart).isNotNull();

        Thread.sleep(5);
        execution = this.executionService.startExecution(execution);
        assertThat(execution.getStart()).as("a second start() call must not move the start time").isEqualTo(
                firstStart);
    }

    @Test
    void finishExecutionStopsAndPaysThePrice() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        this.creditService.inpayment(user, new BigDecimal("50.00"));

        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        execution = this.executionService.startExecution(execution);
        execution = this.executionService.finishExecution(execution);

        assertThat(execution.isFinished()).isTrue();
        assertThat(execution.getStop()).isNotNull();
        // 50.00 (Einzahlung) - 3.00 (Grundgebühr des FIXED-Programms, Preis unabhängig von
        // der winzigen Testlaufzeit, da free_duration=0) = 47.00
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("47.00");
    }

    @Test
    void resetExecutionMarksItFinishedWithoutStartOrStopLikeLegacyExecutionReset() {
        // Beobachtung (siehe ExecutionService#resetExecution Javadoc und
        // docs/kb/05-migration-plan.md "Beobachtungen"): trotz des Methodennamens "reset" wird
        // finished=TRUE gesetzt, nicht FALSE - 1:1 wie der Alt-Code
        // (org.kabieror.elwasys.common.Execution#reset()).
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        execution = this.executionService.startExecution(execution);

        execution = this.executionService.resetExecution(execution);

        assertThat(execution.getStart()).isNull();
        assertThat(execution.getStop()).isNull();
        assertThat(execution.isFinished()).as("reset() sets finished=TRUE, not FALSE - see Javadoc").isTrue();

        // Eine zurückgesetzte Ausführung kostet nichts (getPrice prüft start==null zuerst).
        assertThat(this.executionService.getPrice(execution)).isEqualByComparingTo(BigDecimal.ZERO);
        // ... und zählt nicht mehr als "nicht abgeschlossen" - mindert das Guthaben also
        // nicht (mehr).
        assertThat(this.executionService.getNotFinishedExecutions(user)).isEmpty();
    }

    @Test
    void isExpiredAndHasExpiredExecutionsFollowMaxDuration() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        // 0 Sekunden Maximaldauer: sofort nach dem Start bereits abgelaufen.
        ProgramEntity program = newProgram(0);

        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        assertThat(this.executionService.isExpired(execution)).as("not started yet -> not expired").isFalse();

        execution = this.executionService.startExecution(execution);
        assertThat(this.executionService.isExpired(execution)).as(
                "max duration 0s already exceeded right after start").isTrue();
        assertThat(this.executionService.hasExpiredExecutions(user)).isTrue();

        this.executionService.finishExecution(execution);
        assertThat(this.executionService.hasExpiredExecutions(user)).as("finished executions are never expired")
                .isFalse();
    }

    @Test
    void getRunningExecutionSkipsExpiredExecutionsLikeLegacyDataManager() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity expiredProgram = newProgram(0);

        ExecutionEntity execution = this.executionService.createExecution(device, expiredProgram, user);
        execution = this.executionService.startExecution(execution);
        assertThat(this.executionService.isExpired(execution)).isTrue();

        Optional<ExecutionEntity> running = this.executionService.getRunningExecution(device);
        assertThat(running).as("an expired-but-not-yet-finished execution must not be reported as 'running'")
                .isEmpty();
    }

    @Test
    void getRunningExecutionReturnsTheCurrentlyRunningOne() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);

        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        execution = this.executionService.startExecution(execution);

        Optional<ExecutionEntity> running = this.executionService.getRunningExecution(device);
        assertThat(running).isPresent();
        assertThat(running.get().getId()).isEqualTo(execution.getId());

        this.executionService.finishExecution(execution);
        assertThat(this.executionService.getRunningExecution(device)).isEmpty();
    }

    @Test
    void getLastUserReturnsTheUserOfTheMostRecentStartedExecution() {
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        UserEntity firstUser = newUser();
        UserEntity secondUser = newUser();

        ExecutionEntity first = this.executionService.createExecution(device, program, firstUser);
        this.executionService.finishExecution(this.executionService.startExecution(first));

        ExecutionEntity second = this.executionService.createExecution(device, program, secondUser);
        this.executionService.finishExecution(this.executionService.startExecution(second));

        Optional<UserEntity> lastUser = this.executionService.getLastUser(device);
        assertThat(lastUser).isPresent();
        assertThat(lastUser.get().getId()).isEqualTo(secondUser.getId());
    }

    @Test
    void getExpiredExecutionsReturnsOnlyExpiredNotFinishedOnes() {
        // 1:1-Portierung des Filters aus ExpiredExecutionsWindow (Alt-Portal, Phase 3 AP4).
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity expiredProgram = newProgram(0);
        ProgramEntity runningProgram = newProgram(3600);

        ExecutionEntity expired = this.executionService.startExecution(
                this.executionService.createExecution(device, expiredProgram, user));
        // Nicht abgelaufen (lange Maximaldauer) - darf nicht in der Liste erscheinen.
        this.executionService.startExecution(this.executionService.createExecution(device, runningProgram, user));

        List<ExecutionEntity> expiredExecutions = this.executionService.getExpiredExecutions(user);
        assertThat(expiredExecutions).hasSize(1);
        assertThat(expiredExecutions.get(0).getId()).isEqualTo(expired.getId());

        this.executionService.finishExecution(expired);
        assertThat(this.executionService.getExpiredExecutions(user)).as("finished executions are never expired")
                .isEmpty();
    }

    @Test
    void deleteRemovesTheExecution() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(0);
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(device, program, user));
        Integer id = execution.getId();
        assertThat(this.executionRepository.findById(id)).isPresent();

        this.executionService.delete(execution);

        assertThat(this.executionRepository.findById(id)).isEmpty();
    }

    @Test
    void startExecutionWithClientTimestampUsesItInsteadOfServerNow() {
        // AP3, Phase 4 (additiv, siehe docs/kb/05-migration-plan.md "Idempotenz + Replay" und
        // ExecutionStartRequest#clientTimestamp Javadoc): ein vom Terminal mitgelieferter
        // Original-Zeitstempel wird 1:1 übernommen statt der Serverzeit.
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        LocalDateTime clientTimestamp = LocalDateTime.of(2020, 1, 1, 10, 0, 0);

        execution = this.executionService.startExecution(execution, clientTimestamp);

        assertThat(execution.getStart()).isEqualTo(clientTimestamp);
    }

    @Test
    void startExecutionWithoutClientTimestampFallsBackToServerNowLikeBefore() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        LocalDateTime before = LocalDateTime.now();

        execution = this.executionService.startExecution(execution, null);

        assertThat(execution.getStart()).isNotNull();
        assertThat(execution.getStart()).isAfterOrEqualTo(before);
    }

    @Test
    void finishExecutionWithClientTimestampSetsTheOriginalStopTime() {
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        this.creditService.inpayment(user, new BigDecimal("50.00"));
        LocalDateTime startTimestamp = LocalDateTime.of(2020, 1, 1, 10, 0, 0);
        LocalDateTime stopTimestamp = LocalDateTime.of(2020, 1, 1, 12, 0, 0);
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(device, program, user), startTimestamp);

        execution = this.executionService.finishExecution(execution, stopTimestamp);

        assertThat(execution.getStop()).isEqualTo(stopTimestamp);
        assertThat(execution.isFinished()).isTrue();
    }

    @Test
    void finishExecutionClampsStopToStartWhenClientTimestampIsBeforeStart() {
        // Issue #18, Szenario 2 (negative Dauer): eine stark verspätete Offline-Nachmeldung,
        // deren Start-Zeitstempel unabhängig auf die Serverzeit ersetzt wurde, kann einen
        // Finish-Zeitstempel VOR dem Start erzeugen. Ohne Klemmung entstünde ein Datensatz mit
        // stop < start und - über die Freiminuten-Regel - ein 0-€-Waschgang. Der Stop wird auf
        // den Start geklemmt (Dauer 0), stop >= start gilt in der DB.
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        this.creditService.inpayment(user, new BigDecimal("50.00"));
        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 10, 0, 0);
        LocalDateTime finishBeforeStart = LocalDateTime.of(2020, 1, 1, 9, 0, 0);
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(device, program, user), start);

        execution = this.executionService.finishExecution(execution, finishBeforeStart);

        assertThat(execution.getStop()).as("stop must never be before start (no negative duration in DB)")
                .isEqualTo(start);
        assertThat(execution.getStop()).isAfterOrEqualTo(execution.getStart());
        // Dauer 0 -> Freiminuten-Regel -> kein Abzug, Guthaben unverändert.
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("50.00");
    }

    @Test
    void getPriceForAFinishedExecutionIsCappedAtProgramMaxDuration() {
        // Issue #18, Szenario 1 (Überberechnung): ein langer Backend-Ausfall während eines online
        // gestarteten Waschgangs führt beim Replay zu einem Finish-Zeitstempel, der über die
        // Maximaldauer hinaus zurückliegt. Der Preis eines DYNAMIC-Programms darf trotzdem nie
        // über den beim Start geprüften maxPrice (Preis bei Maximaldauer) hinausgehen.
        UserEntity user = newUser();
        DeviceEntity device = newDevice();
        ProgramEntity program = newDynamicHourlyProgram(3600); // maxDuration 1h
        this.creditService.inpayment(user, new BigDecimal("50.00"));
        LocalDateTime start = LocalDateTime.of(2020, 1, 1, 10, 0, 0);
        LocalDateTime finishThreeHoursLater = LocalDateTime.of(2020, 1, 1, 13, 0, 0);
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(device, program, user), start);

        execution = this.executionService.finishExecution(execution, finishThreeHoursLater);

        // maxPrice = Grundgebühr 1.00 + 2.00/h * 1h = 3.00 (nicht 1.00 + 2.00*3 = 7.00).
        assertThat(this.executionService.getPrice(execution)).isEqualByComparingTo("3.00");
        assertThat(this.creditService.getCredit(user)).as("only the capped price is charged")
                .isEqualByComparingTo("47.00");
        // Der reale End-Zeitstempel bleibt als Audit-Record erhalten (nur der Preis ist gedeckelt).
        assertThat(execution.getStop()).isEqualTo(finishThreeHoursLater);
    }

    @Test
    void getExecutionsReturnsOnlyStartedOnesNewestFirst() {
        DeviceEntity device = newDevice();
        ProgramEntity program = newProgram(3600);
        UserEntity user = newUser();

        // Nie gestartete Ausführung darf NICHT in der Liste auftauchen (Alt-Code:
        // "start IS NOT NULL").
        this.executionService.createExecution(device, program, user);

        ExecutionEntity started = this.executionService.createExecution(device, program, user);
        started = this.executionService.startExecution(started);

        List<ExecutionEntity> executions = this.executionService.getExecutions(device);
        assertThat(executions).hasSize(1);
        assertThat(executions.get(0).getId()).isEqualTo(started.getId());
    }
}
