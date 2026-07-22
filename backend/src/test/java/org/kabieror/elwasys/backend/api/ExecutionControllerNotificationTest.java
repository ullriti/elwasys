package org.kabieror.elwasys.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verdrahtung der Ende-/Abbruch-Benachrichtigung an den API-Execution-Lebenszyklus (AP3, siehe
 * docs/kb/05-migration-plan.md und {@code ExecutionController} Klassen-Javadoc,
 * "Benachrichtigungen"). Seit Issue #36 publiziert der Controller ein
 * {@link ExecutionNotificationEvent} (statt den {@code NotificationService} direkt aufzurufen);
 * der eigentliche Versand läuft danach im {@code ExecutionNotificationListener} erst nach dem
 * Commit - dieser Test prüft daher, dass GENAU das richtige Ereignis (Ende vs. Abbruch, nur
 * einmal je Idempotenz-Schlüssel, nie beim Reset) publiziert wird. Die Zuordnung
 * Ereignis→{@code NotificationService} deckt {@link ExecutionNotificationListenerTest} ab.
 *
 * <p>Bewusst ein reiner Mockito-Unit-Test OHNE Spring-Kontext (kein
 * {@code @SpringBootTest}/{@code AbstractApiIT}): ein zusätzlicher, sich unterscheidender
 * Kontext würde einen weiteren gecachten Spring-Testkontext samt eigenem Connection-Pool
 * erzeugen - genau das Muster, das in Phase 3 AP4 bereits einmal "PostgreSQL
 * max_connections=100" überschritten hat (siehe docs/kb/05-migration-plan.md).
 */
class ExecutionControllerNotificationTest {

    private ApplicationEventPublisher eventPublisher;

    private ExecutionService executionService;

    private ExecutionController controller;

    private TerminalPrincipal terminal;

    private ExecutionEntity execution;

    @BeforeEach
    void setUp() {
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        this.executionService = mock(ExecutionService.class);
        AdvisoryLockService advisoryLockService = mock(AdvisoryLockService.class);
        TerminalScopeGuard scopeGuard = mock(TerminalScopeGuard.class);

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Ein bloßer Mockito-Mock des Repositorys würde findByIdempotencyKey() immer leer
        // beantworten (Mockito-Default für Optional-Rückgaben) - das würde jeden Aufruf wie
        // einen NEUEN Schlüssel behandeln und die Dedup-Tests wären wirkungslos. Diese
        // kleine In-Memory-Fake-Implementierung bildet die einzigen zwei von
        // IdempotencyService genutzten Methoden nach.
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

        this.controller = new ExecutionController(null, null, mock(PermissionService.class),
                mock(PricingService.class), mock(CreditService.class), this.executionService, scopeGuard,
                idempotencyService, new ClientTimestampPolicy(new OfflineProperties()), advisoryLockService,
                this.eventPublisher);

        LocationEntity location = new LocationEntity("Waschkeller");
        DeviceEntity device = new DeviceEntity("Waschmaschine 1", 0, location);
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        UserEntity user = new UserEntity("Erika Mustermann", "erika", group);
        ProgramEntity program = new ProgramEntity("Kurzprogramm", ProgramType.FIXED, 3600);
        this.execution = new ExecutionEntity(device, program, user);

        this.terminal = new TerminalPrincipal(1, 1, "Waschkeller");
        when(scopeGuard.requireExecutionInScope(anyInt(), any())).thenReturn(this.execution);
        // Der Finish-/Abort-Pfad lädt die Ausführung frisch und gesperrt (Issue #20) - im
        // Unit-Test liefert der Mock dieselbe Instanz zurück.
        when(this.executionService.getForUpdate(anyInt())).thenReturn(this.execution);
        // thenAnswer statt thenReturn: markiert die (echte, nicht gemockte) ExecutionEntity
        // tatsächlich als beendet, damit executionAlreadyFinishedIsCheckedInsideThe
        // IdempotencyBranch() den echten Fallstrick nachstellen kann (siehe dortiger
        // Kommentar) - mit einem simplen thenReturn bliebe execution.isFinished() immer
        // false und der Test würde nichts beweisen.
        when(this.executionService.finishExecution(any(), any())).thenAnswer(invocation -> {
            this.execution.setFinished(true);
            return this.execution;
        });
        when(this.executionService.getPrice(any())).thenReturn(BigDecimal.ZERO);
    }

    private ExecutionNotificationEvent finishedEvent() {
        return new ExecutionNotificationEvent(this.execution.getUser(), this.execution.getDevice(), false);
    }

    private ExecutionNotificationEvent abortedEvent() {
        return new ExecutionNotificationEvent(this.execution.getUser(), this.execution.getDevice(), true);
    }

    @Test
    void finishPublishesFinishedNotAbortedEvent() {
        this.controller.finish(this.terminal, 1, null, null);

        verify(this.eventPublisher, times(1)).publishEvent(finishedEvent());
        verify(this.eventPublisher, never()).publishEvent(abortedEvent());
    }

    @Test
    void abortPublishesAbortedNotFinishedEvent() {
        this.controller.abort(this.terminal, 1, null, null);

        verify(this.eventPublisher, times(1)).publishEvent(abortedEvent());
        verify(this.eventPublisher, never()).publishEvent(finishedEvent());
    }

    @Test
    void resetDoesNotPublishAnyNotificationEvent() {
        when(this.executionService.resetExecution(any())).thenReturn(this.execution);

        this.controller.reset(this.terminal, 1, null);

        verify(this.eventPublisher, never()).publishEvent(any(ExecutionNotificationEvent.class));
    }

    @Test
    void repeatedFinishWithSameIdempotencyKeyPublishesOnlyOnce() {
        String key = UUID.randomUUID().toString();

        this.controller.finish(this.terminal, 1, key, null);
        // Der zweite Aufruf mit demselben Schlüssel liefert die gespeicherte Antwort erneut
        // aus - executionService/eventPublisher dürfen dabei NICHT erneut aufgerufen werden
        // (siehe IdempotencyService Javadoc).
        this.controller.finish(this.terminal, 1, key, null);

        verify(this.eventPublisher, times(1)).publishEvent(any(ExecutionNotificationEvent.class));
        verify(this.executionService, times(1)).finishExecution(any(), any());
    }

    @Test
    void executionAlreadyFinishedIsCheckedInsideTheIdempotencyBranch() {
        // Regressionstest für den in AP3 gefundenen Fallstrick (siehe ExecutionController
        // Javadoc zu finishOrAbort): der "bereits beendet"-Wächter darf einen Replay mit
        // demselben Idempotenz-Schlüssel nicht mit 409 statt der gespeicherten Antwort
        // beenden.
        String key = UUID.randomUUID().toString();

        this.controller.finish(this.terminal, 1, key, null);
        var second = this.controller.finish(this.terminal, 1, key, null);

        assertThat(second).isNotNull();
        verify(this.executionService, times(1)).finishExecution(any(), any());
    }
}
