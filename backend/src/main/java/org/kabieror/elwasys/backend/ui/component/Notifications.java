package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Die zwei Rückmeldungen, die das Portal kennt: Fehler (rot, 5 s) und Erfolg (grün, 4 s), beide
 * mittig. Sie waren zuvor in sechs Ansichten/Dialogen wortgleich als private Methode dupliziert
 * und an weiteren Stellen inline aufgebaut (finale Review R3c, Issue #92) - mit dem Risiko, dass
 * eine Kopie beim nächsten Anfassen abweicht und Rückmeldungen im Portal uneinheitlich aussehen.
 * Die Werte sind exakt die der bisherigen Kopien; das Erscheinungsbild ändert sich nicht.
 */
public final class Notifications {

    /**
     * Anzeigedauer einer Fehlermeldung. Länger als beim Erfolg, weil eine Fehlermeldung gelesen
     * werden muss, bevor sie verschwindet.
     */
    private static final int ERROR_DURATION_MS = 5000;

    private static final int SUCCESS_DURATION_MS = 4000;

    private Notifications() {
        // Utility-Klasse
    }

    /**
     * Zeigt eine Fehlermeldung an (rot, mittig).
     */
    public static void showError(String message) {
        Notification notification = Notification.show(message, ERROR_DURATION_MS, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /**
     * Zeigt eine Erfolgsmeldung an (grün, mittig).
     */
    public static void showSuccess(String message) {
        Notification notification = Notification.show(message, SUCCESS_DURATION_MS, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
