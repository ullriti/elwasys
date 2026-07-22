package org.kabieror.elwasys.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.notification.ExecutionNotificationEvent;
import org.kabieror.elwasys.backend.notification.ExecutionNotificationListener;
import org.kabieror.elwasys.backend.notification.NotificationService;

/**
 * Zuordnung {@link ExecutionNotificationEvent} → {@link NotificationService} (Issue #36 - AP3).
 * Der Listener wird von Spring erst nach dem Commit der Finish-/Abort-Transaktion aufgerufen
 * ({@code @TransactionalEventListener}, siehe {@code ExecutionNotificationListener}); diese
 * Transaktions-Kopplung ist Spring-Infrastruktur und hier bewusst nicht Gegenstand des Tests -
 * geprüft wird die reine Verzweigungslogik (Ende vs. Abbruch) als schneller Mockito-Unit-Test.
 */
class ExecutionNotificationListenerTest {

    private NotificationService notificationService;
    private ExecutionNotificationListener listener;
    private UserEntity user;
    private DeviceEntity device;

    @BeforeEach
    void setUp() {
        this.notificationService = mock(NotificationService.class);
        this.listener = new ExecutionNotificationListener(this.notificationService);

        LocationEntity location = new LocationEntity("Waschkeller");
        this.device = new DeviceEntity("Waschmaschine 1", 0, location);
        UserGroupEntity group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        this.user = new UserEntity("Erika Mustermann", "erika", group);
    }

    @Test
    void finishedEventTriggersFinishedNotification() {
        this.listener.onExecutionNotification(new ExecutionNotificationEvent(this.user, this.device, false));

        verify(this.notificationService, times(1)).notifyExecutionFinished(this.user, this.device);
        verify(this.notificationService, never()).notifyExecutionAborted(any(), any());
    }

    @Test
    void abortedEventTriggersAbortedNotification() {
        this.listener.onExecutionNotification(new ExecutionNotificationEvent(this.user, this.device, true));

        verify(this.notificationService, times(1)).notifyExecutionAborted(this.user, this.device);
        verify(this.notificationService, never()).notifyExecutionFinished(any(), any());
    }
}
