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
import org.kabieror.elwasys.backend.notification.NotificationService;
import org.kabieror.elwasys.backend.offline.ClientTimestampPolicy;
import org.kabieror.elwasys.backend.offline.OfflineProperties;
import org.kabieror.elwasys.backend.repository.TerminalIdempotencyKeyRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;

/**
 * Verdrahtung des Benachrichtigungsdienstes an den API-Execution-Lebenszyklus (AP3, Phase 4,
 * siehe kb/05-migration-plan.md und {@code ExecutionController} Klassen-Javadoc,
 * "Benachrichtigungen"). Bewusst ein reiner Mockito-Unit-Test OHNE Spring-Kontext (kein
 * {@code @SpringBootTest}/{@code AbstractApiIT}): ein zusätzlicher, sich per {@code @MockBean}
 * unterscheidender Kontext würde einen weiteren gecachten Spring-Testkontext samt eigenem
 * Connection-Pool erzeugen - genau das Muster, das in Phase 3 AP4 bereits einmal
 * "PostgreSQL max_connections=100" überschritten hat (siehe kb/05-migration-plan.md,
 * Änderungslog "Phase 3 AP4", Fallstrick). Der Controller ist reines Java (keine
 * Spring-MVC-Reflektion nötig, um seine Methoden direkt aufzurufen), ein Mockito-Unit-Test
 * prüft die Verdrahtungslogik daher ebenso zuverlässig, aber ohne jede DB-Verbindung. Das
 * Notification-GATING selbst (inkl. Flag-AUS-Fall) ist bereits auf Ebene von
 * {@link NotificationService} durch {@code NotificationServiceEmailTest}/
 * {@code NotificationsPropertiesDefaultTest} abgedeckt (siehe kb/03-modules.md).
 */
class ExecutionControllerNotificationTest {

    private NotificationService notificationService;

    private ExecutionService executionService;

    private ExecutionController controller;

    private TerminalPrincipal terminal;

    private ExecutionEntity execution;

    @BeforeEach
    void setUp() {
        this.notificationService = mock(NotificationService.class);
        this.executionService = mock(ExecutionService.class);
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
        IdempotencyService idempotencyService = new IdempotencyService(fakeRepository, objectMapper);

        this.controller = new ExecutionController(null, null, mock(PermissionService.class),
                mock(PricingService.class), mock(CreditService.class), this.executionService, scopeGuard,
                idempotencyService, this.notificationService,
                new ClientTimestampPolicy(new OfflineProperties()));

        LocationEntity location = new LocationEntity("Waschkeller");
        DeviceEntity device = new DeviceEntity("Waschmaschine 1", 0, location);
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        UserEntity user = new UserEntity("Erika Mustermann", "erika", group);
        ProgramEntity program = new ProgramEntity("Kurzprogramm", ProgramType.FIXED, 3600);
        this.execution = new ExecutionEntity(device, program, user);

        this.terminal = new TerminalPrincipal(1, 1, "Waschkeller");
        when(scopeGuard.requireExecutionInScope(anyInt(), any())).thenReturn(this.execution);
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

    @Test
    void finishTriggersNotifyExecutionFinishedNotAborted() {
        this.controller.finish(this.terminal, 1, null, null);

        verify(this.notificationService, times(1)).notifyExecutionFinished(this.execution.getUser(),
                this.execution.getDevice());
        verify(this.notificationService, never()).notifyExecutionAborted(any(), any());
    }

    @Test
    void abortTriggersNotifyExecutionAbortedNotFinished() {
        this.controller.abort(this.terminal, 1, null, null);

        verify(this.notificationService, times(1)).notifyExecutionAborted(this.execution.getUser(),
                this.execution.getDevice());
        verify(this.notificationService, never()).notifyExecutionFinished(any(), any());
    }

    @Test
    void resetDoesNotTriggerAnyNotification() {
        when(this.executionService.resetExecution(any())).thenReturn(this.execution);

        this.controller.reset(this.terminal, 1, null);

        verify(this.notificationService, never()).notifyExecutionFinished(any(), any());
        verify(this.notificationService, never()).notifyExecutionAborted(any(), any());
    }

    @Test
    void repeatedFinishWithSameIdempotencyKeyNotifiesOnlyOnce() {
        String key = UUID.randomUUID().toString();

        this.controller.finish(this.terminal, 1, key, null);
        // Der zweite Aufruf mit demselben Schlüssel liefert die gespeicherte Antwort erneut
        // aus - executionService/notificationService dürfen dabei NICHT erneut aufgerufen
        // werden (siehe IdempotencyService Javadoc).
        this.controller.finish(this.terminal, 1, key, null);

        verify(this.notificationService, times(1)).notifyExecutionFinished(any(), any());
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
