package org.kabieror.elwasys.backend.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Versendet die Ende-/Abbruch-Benachrichtigung erst NACH dem Commit der auslösenden
 * Finish-/Abort-Transaktion (Issue #36 - AP3, siehe {@link ExecutionNotificationEvent} und
 * docs/kb/05-migration-plan.md). Spiegelt das bereits etablierte Muster von
 * {@link org.kabieror.elwasys.backend.ui.push.UiBroadcaster} (Domain-Events werden ebenfalls
 * per {@link TransactionalEventListener} in der Default-Phase {@code AFTER_COMMIT} verteilt):
 * der eigentliche - potentiell langsame und fehleranfällige - SMTP-/Pushover-Versand läuft so
 * außerhalb der DB-Transaktion.
 *
 * <p>Bewusst OHNE {@code fallbackExecution=true}: dieses Ereignis wird ausschließlich aus einer
 * laufenden Transaktion publiziert (der Idempotenz-Pfad in
 * {@link org.kabieror.elwasys.backend.api.ExecutionController} ist immer transaktional). Wird
 * die Transaktion zurückgerollt, soll gerade KEINE Benachrichtigung erfolgen - genau das
 * Verhalten, das ein fehlender Fallback garantiert.
 */
@Component
public class ExecutionNotificationListener {

    private final NotificationService notificationService;

    public ExecutionNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener
    public void onExecutionNotification(ExecutionNotificationEvent event) {
        if (event.aborted()) {
            this.notificationService.notifyExecutionAborted(event.user(), event.device());
        } else {
            this.notificationService.notifyExecutionFinished(event.user(), event.device());
        }
    }
}
