package org.kabieror.elwasys.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.api.dto.ExecutionEndRequest;
import org.kabieror.elwasys.backend.api.dto.ExecutionStartRequest;
import org.kabieror.elwasys.backend.api.exception.InvalidReplayTimestampException;
import org.kabieror.elwasys.backend.api.exception.UserBlockedException;
import org.kabieror.elwasys.backend.api.idempotency.IdempotencyService;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TerminalIdempotencyKeyEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.notification.ExecutionNotificationEvent;
import org.kabieror.elwasys.backend.offline.ClientTimestampPolicy;
import org.kabieror.elwasys.backend.offline.OfflineProperties;
import org.kabieror.elwasys.backend.repository.TerminalIdempotencyKeyRepository;
import org.kabieror.elwasys.backend.service.AdvisoryLockService;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Zeitstempel-Toleranz und Benachrichtigungs-Unterdrückung für Offline-Nachmeldungen (Phase 4
 * AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal" Punkt 4
 * und "Festlegungen zu den Offline-Detailfragen" sowie {@link ClientTimestampPolicy}
 * Javadoc). Reiner Mockito-Unit-Test ohne Spring-Kontext, gleiches Muster wie
 * {@link ExecutionControllerNotificationTest} (Begründung siehe dort).
 */
class ExecutionControllerOfflineReplayTest {

    private ExecutionService executionService;

    private ApplicationEventPublisher eventPublisher;

    private ExecutionController controller;

    private TerminalPrincipal terminal;

    private DeviceEntity device;

    private ProgramEntity program;

    private UserEntity user;

    private ExecutionEntity execution;

    private CreditService creditService;

    private org.kabieror.elwasys.backend.repository.ProgramRepository programRepository;

    private org.kabieror.elwasys.backend.repository.UserRepository userRepository;

    private void setUp(int offlineMaxDurationMinutes) {
        this.executionService = mock(ExecutionService.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        AdvisoryLockService advisoryLockService = mock(AdvisoryLockService.class);
        PermissionService permissionService = mock(PermissionService.class);
        PricingService pricingService = mock(PricingService.class);
        CreditService creditService = mock(CreditService.class);
        this.creditService = creditService;
        TerminalScopeGuard scopeGuard = mock(TerminalScopeGuard.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Siehe ExecutionControllerNotificationTest fuer die Begruendung dieser Fake-Repository-
        // Implementierung statt eines bloszen Mocks.
        Map<String, TerminalIdempotencyKeyEntity> store = new HashMap<>();
        TerminalIdempotencyKeyRepository fakeRepository = mock(TerminalIdempotencyKeyRepository.class);
        when(fakeRepository.findByIdempotencyKey(any())).thenAnswer(
                invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
        when(fakeRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TerminalIdempotencyKeyEntity entity = invocation.getArgument(0);
            store.put(entity.getIdempotencyKey(), entity);
            return entity;
        });
        IdempotencyService idempotencyService = new IdempotencyService(fakeRepository, objectMapper,
                advisoryLockService);

        OfflineProperties offlineProperties = new OfflineProperties();
        offlineProperties.setClockDriftTolerance(Duration.ofMinutes(5));
        ClientTimestampPolicy clientTimestampPolicy = new ClientTimestampPolicy(offlineProperties);

        LocationEntity location = new LocationEntity("Waschkeller");
        location.setOfflineMaxDurationMinutes(offlineMaxDurationMinutes);
        this.device = new DeviceEntity("Waschmaschine 1", 0, location);
        // Der Start-Pfad sperrt das Gerät per Advisory-Lock über device.getId() (Issue #20) -
        // eine transiente Test-Entität hätte hier null; in Produktion liefert der Standort-Scope-
        // Wächter immer eine persistierte Entität mit Id.
        org.springframework.test.util.ReflectionTestUtils.setField(this.device, "id", 1);
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        this.user = new UserEntity("Erika Mustermann", "erika", group);
        this.program = new ProgramEntity("Kurzprogramm", ProgramType.FIXED, 3600);
        this.execution = new ExecutionEntity(this.device, this.program, this.user);

        this.terminal = new TerminalPrincipal(1, 1, "Waschkeller");
        when(scopeGuard.requireExecutionInScope(anyInt(), any())).thenReturn(this.execution);
        when(scopeGuard.requireDeviceInScope(anyInt(), any())).thenReturn(this.device);
        when(permissionService.isUserAllowedAtLocation(any(), any())).thenReturn(true);
        when(permissionService.isDeviceUsableByUser(any(), any())).thenReturn(true);
        when(permissionService.isProgramAvailableForDeviceAndUser(any(), any(), any())).thenReturn(true);
        when(this.executionService.getRunningExecution(any())).thenReturn(Optional.empty());
        when(creditService.canAfford(any(), any())).thenReturn(true);
        when(this.executionService.createExecution(any(), any(), any())).thenReturn(this.execution);
        when(this.executionService.startExecution(any(), any())).thenReturn(this.execution);
        when(this.executionService.getForUpdate(anyInt())).thenReturn(this.execution);
        when(this.executionService.finishExecution(any(), any())).thenAnswer(invocation -> {
            this.execution.setFinished(true);
            return this.execution;
        });
        when(this.executionService.getPrice(any())).thenReturn(BigDecimal.ZERO);

        this.programRepository = mock(org.kabieror.elwasys.backend.repository.ProgramRepository.class);
        when(this.programRepository.findById(any())).thenReturn(Optional.of(this.program));
        this.userRepository = mock(org.kabieror.elwasys.backend.repository.UserRepository.class);
        when(this.userRepository.findById(any())).thenReturn(Optional.of(this.user));
        // Nicht-Replay-Starts laden den Nutzer frisch und gesperrt (Issue #20).
        when(this.userRepository.findWithLockById(any())).thenReturn(Optional.of(this.user));

        this.controller = new ExecutionController(this.programRepository, this.userRepository, permissionService,
                pricingService, creditService, this.executionService, scopeGuard, idempotencyService,
                clientTimestampPolicy, advisoryLockService, this.eventPublisher);
    }

    @Test
    void clientTimestampWithinTheOfflineWindowIsUsedAsIs() {
        setUp(60);
        LocalDateTime withinWindow = LocalDateTime.now().minusMinutes(30);

        this.controller.start(this.terminal, null, new ExecutionStartRequest(1, 1, 1, withinWindow, null));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.executionService).startExecution(eq(this.execution), captor.capture());
        assertThat(captor.getValue()).isEqualTo(withinWindow);
    }

    @Test
    void clientTimestampOlderThanTheOfflineWindowPlusToleranceIsReplacedByServerTime() {
        // offline.max-duration = 5min + 5min Toleranz = 10min Fenster.
        setUp(5);
        LocalDateTime tooOld = LocalDateTime.now().minusMinutes(200);

        this.controller.start(this.terminal, null, new ExecutionStartRequest(1, 1, 1, tooOld, null));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.executionService).startExecution(eq(this.execution), captor.capture());
        assertThat(captor.getValue()).isNotEqualTo(tooOld);
        assertThat(captor.getValue()).isCloseTo(LocalDateTime.now(),
                org.assertj.core.api.Assertions.within(10, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void clientTimestampWithinClockDriftToleranceOfTheFutureIsUsedAsIs() {
        setUp(60);
        LocalDateTime slightlyInTheFuture = LocalDateTime.now().plusMinutes(2);

        this.controller.start(this.terminal, null, new ExecutionStartRequest(1, 1, 1, slightlyInTheFuture, null));

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.executionService).startExecution(eq(this.execution), captor.capture());
        assertThat(captor.getValue()).isEqualTo(slightlyInTheFuture);
    }

    @Test
    void missingClientTimestampStaysNull() {
        setUp(60);

        this.controller.start(this.terminal, null, new ExecutionStartRequest(1, 1, 1, null, null));

        verify(this.executionService).startExecution(this.execution, null);
    }

    @Test
    void notificationIsSuppressedForAReplayedEventOlderThanTheOfflineMaxDuration() {
        setUp(60);
        LocalDateTime tooOldForNotification = LocalDateTime.now().minusMinutes(90);

        this.controller.finish(this.terminal, 1, null, new ExecutionEndRequest(tooOldForNotification));

        verify(this.eventPublisher, never()).publishEvent(any(ExecutionNotificationEvent.class));
        // Die Ausfuehrung selbst wird trotzdem ganz normal verbucht.
        verify(this.executionService, times(1)).finishExecution(any(), any());
    }

    @Test
    void notificationIsSentForAReplayedEventWithinTheOfflineMaxDuration() {
        setUp(60);
        LocalDateTime recent = LocalDateTime.now().minusMinutes(10);

        this.controller.finish(this.terminal, 1, null, new ExecutionEndRequest(recent));

        verify(this.eventPublisher, times(1)).publishEvent(
                new ExecutionNotificationEvent(this.execution.getUser(), this.execution.getDevice(), false));
    }

    @Test
    void notificationIsSentAsUsualWhenNoClientTimestampIsGiven() {
        setUp(60);

        this.controller.finish(this.terminal, 1, null, null);

        verify(this.eventPublisher, times(1)).publishEvent(
                new ExecutionNotificationEvent(this.execution.getUser(), this.execution.getDevice(), false));
    }

    @Test
    void perLocationOfflineMaxDurationIsRespectedForNotificationSuppression() {
        // 5-Minuten-Standort: ein 8 Minuten altes Ereignis ist hier bereits "zu alt", obwohl
        // es beim 60-Minuten-Standort oben noch benachrichtigt hätte.
        setUp(5);
        LocalDateTime eightMinutesAgo = LocalDateTime.now().minusMinutes(8);

        this.controller.finish(this.terminal, 1, null, new ExecutionEndRequest(eightMinutesAgo));

        verify(this.eventPublisher, never()).publishEvent(any(ExecutionNotificationEvent.class));
    }

    // --- Privilegierter Nachbuchungs-Pfad (Issue #16) ---------------------------------------

    @Test
    void replayStartSkipsBusinessGuardsAndBooksTheFactEvenWithBlockedUserAndInsufficientCredit() {
        // Issue #16: eine Offline-Nachmeldung ist ein FAKT. Alle fachlichen Wächter würden eine
        // Live-Buchung ablehnen (Nutzer zwischenzeitlich gesperrt, Guthaben reicht nicht) - beim
        // Replay müssen sie übersprungen werden, sonst verklemmt der Eintrag das Journal dauerhaft.
        setUp(60);
        this.user.setBlocked(true);
        when(this.creditService.canAfford(any(), any())).thenReturn(false);
        when(this.executionService.getRunningExecution(any()))
                .thenReturn(Optional.of(mock(ExecutionEntity.class)));

        this.controller.start(this.terminal, "replay-key-1",
                new ExecutionStartRequest(1, 1, 1, LocalDateTime.now().minusMinutes(30), Boolean.TRUE));

        // Die Ausführung wird trotz aller Wächter angelegt und gestartet (negatives Guthaben ist
        // beim Replay laut Auftraggeber-Festlegung zulässig).
        verify(this.executionService, times(1)).createExecution(this.device, this.program, this.user);
        verify(this.executionService, times(1)).startExecution(eq(this.execution), any());
    }

    @Test
    void replayReturnsTheStoredResponseEvenIfAReferenceEntityWasDeletedMeanwhile() {
        // Issue #41: Nach einem erfolgreichen Erst-Start werden user/program gelöscht (findById
        // liefert leer). Ein legitimer Replay mit demselben Schlüssel muss die gespeicherte
        // Antwort liefern statt 404 - genau deshalb wird die Entitätsauflösung jetzt im
        // "neu"-Zweig ausgeführt, sodass ein Replay sie gar nicht mehr anfasst.
        setUp(60);
        var request = new ExecutionStartRequest(1, 1, 1, LocalDateTime.now().minusMinutes(5), null);
        var first = this.controller.start(this.terminal, "start-key", request);
        assertThat(first).isNotNull();

        when(this.programRepository.findById(any())).thenReturn(Optional.empty());
        when(this.userRepository.findById(any())).thenReturn(Optional.empty());

        var replayed = this.controller.start(this.terminal, "start-key", request);

        assertThat(replayed).isNotNull();
        // Reiner Replay: keine zweite Ausführung angelegt.
        verify(this.executionService, times(1)).createExecution(any(), any(), any());
    }

    @Test
    void nonReplayStartStillRejectsABlockedUser() {
        // Gegenprobe: ohne Replay-Flag greifen die Wächter unverändert.
        setUp(60);
        this.user.setBlocked(true);

        assertThrows(UserBlockedException.class,
                () -> this.controller.start(this.terminal, "live-key-1",
                        new ExecutionStartRequest(1, 1, 1, LocalDateTime.now().minusMinutes(30), Boolean.FALSE)));

        verify(this.executionService, never()).createExecution(any(), any(), any());
    }

    // --- Zeitstempel-Härtung des Replay-Pfads (Issue #67) -----------------------------------

    @Test
    void replayWithoutAClientTimestampIsRejected() {
        // Issue #67: eine echte Nachmeldung trägt immer den Original-Zeitstempel. Ein Replay
        // OHNE Zeitstempel ist verdächtig (Umgehen der Wächter für eine Live-Buchung) und wird
        // abgelehnt - der Eintrag verklemmt das Terminal-Journal dabei nicht, sondern wandert
        // dort ins Dead-Letter (422, kein Kommunikationsfehler).
        setUp(60);

        assertThrows(InvalidReplayTimestampException.class,
                () -> this.controller.start(this.terminal, "replay-no-ts",
                        new ExecutionStartRequest(1, 1, 1, null, Boolean.TRUE)));

        verify(this.executionService, never()).createExecution(any(), any(), any());
    }

    @Test
    void replayWithANowTimestampIsRejected() {
        // Issue #67: ein "jetzt"-Zeitstempel ist verdächtig - eine echte Offline-Nachmeldung
        // liegt stets deutlich in der Vergangenheit (der Waschgang lief bereits).
        setUp(60);

        assertThrows(InvalidReplayTimestampException.class,
                () -> this.controller.start(this.terminal, "replay-now",
                        new ExecutionStartRequest(1, 1, 1, LocalDateTime.now(), Boolean.TRUE)));

        verify(this.executionService, never()).createExecution(any(), any(), any());
    }

    @Test
    void replayWithAFutureTimestampIsRejected() {
        // Issue #67: ein bereits geschehenes Ereignis kann nicht in der Zukunft liegen.
        setUp(60);

        assertThrows(InvalidReplayTimestampException.class,
                () -> this.controller.start(this.terminal, "replay-future",
                        new ExecutionStartRequest(1, 1, 1, LocalDateTime.now().plusMinutes(10), Boolean.TRUE)));

        verify(this.executionService, never()).createExecution(any(), any(), any());
    }

    @Test
    void replayWithATimestampOlderThanTheOfflineWindowIsStillAccepted() {
        // Issue #67 (Review-Fix): ein Stufe-B-START wird erst nachgemeldet, wenn sein Ende im
        // Journal liegt - sein Alter beim Replay ist Waschdauer + Reconnect und kann das
        // Offline-Fenster des Standorts überschreiten (langer Waschgang). Ein solcher Fall ist
        // LEGITIM und darf NICHT abgelehnt werden (sonst Umsatzverlust); die Serverzeit-Ersetzung
        // in ClientTimestampPolicy#resolve fängt absurd alte Werte ab, ohne die Buchung zu
        // verlieren. Standort mit 5min-Fenster, Zeitstempel 200min alt -> trotzdem angenommen.
        setUp(5);

        this.controller.start(this.terminal, "replay-long-wash",
                new ExecutionStartRequest(1, 1, 1, LocalDateTime.now().minusMinutes(200), Boolean.TRUE));

        verify(this.executionService, times(1)).createExecution(this.device, this.program, this.user);
        verify(this.executionService, times(1)).startExecution(eq(this.execution), any());
    }

    @Test
    void replayWithAPlausiblePastTimestampIsAccepted() {
        // Issue #67: die Gegenprobe - ein plausibel in der Vergangenheit liegender Zeitstempel
        // (deutlich älter als replay-min-backdating, jünger als das Offline-Fenster) wird
        // angenommen und der Fakt trotz aller Wächter verbucht (Issue #16 bleibt erhalten).
        setUp(60);
        this.user.setBlocked(true);
        when(this.creditService.canAfford(any(), any())).thenReturn(false);

        this.controller.start(this.terminal, "replay-ok",
                new ExecutionStartRequest(1, 1, 1, LocalDateTime.now().minusMinutes(30), Boolean.TRUE));

        verify(this.executionService, times(1)).createExecution(this.device, this.program, this.user);
        verify(this.executionService, times(1)).startExecution(eq(this.execution), any());
    }
}
