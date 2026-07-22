package org.kabieror.elwasys.backend.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transaktions-Kopplung des Benachrichtigungsversands (Issue #36 - AP3): der
 * {@link ExecutionNotificationListener} hört per {@code @TransactionalEventListener} in der
 * Phase {@code AFTER_COMMIT}. Dieser Test belegt beide Richtungen an einer echten Transaktion:
 * committet sie, wird versandt; wird sie zurückgerollt, NICHT - genau das Verhalten, das der
 * frühere In-Transaktions-Versand nicht garantierte (eine "fertig"-Mail zu einer nicht
 * verbuchten Ausführung).
 */
class ExecutionNotificationTransactionalTest extends AbstractBackendIT {

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutionNotificationEvent finishedEvent() {
        LocationEntity location = new LocationEntity("Waschkeller");
        DeviceEntity device = new DeviceEntity("Waschmaschine 1", 0, location);
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        UserEntity user = new UserEntity("Erika Mustermann", "erika", group);
        return new ExecutionNotificationEvent(user, device, false);
    }

    @Test
    void notificationIsSentAfterTheTransactionCommits() {
        ExecutionNotificationEvent event = finishedEvent();

        new TransactionTemplate(this.transactionManager).executeWithoutResult(status ->
                this.eventPublisher.publishEvent(event));

        // AFTER_COMMIT läuft synchron nach dem Commit - der Versand ist danach bereits erfolgt.
        verify(this.notificationService, times(1)).notifyExecutionFinished(event.user(), event.device());
    }

    @Test
    void noNotificationIsSentWhenTheTransactionRollsBack() {
        ExecutionNotificationEvent event = finishedEvent();

        new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> {
            this.eventPublisher.publishEvent(event);
            // Transaktion zurückrollen, nachdem das Ereignis publiziert wurde.
            status.setRollbackOnly();
        });

        verify(this.notificationService, never()).notifyExecutionFinished(any(), any());
        verify(this.notificationService, never()).notifyExecutionAborted(any(), any());
    }
}
