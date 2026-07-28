package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.slf4j.LoggerFactory;

/**
 * Die zwei Rückmeldungen, die das Portal kennt: Fehler (rot, 5 s) und Erfolg (grün, 4 s), beide
 * mittig. Sie waren zuvor in sechs Ansichten/Dialogen wortgleich als private Methode dupliziert
 * und an weiteren Stellen inline aufgebaut (finale Review R3c, Issue #92) - mit dem Risiko, dass
 * eine Kopie beim nächsten Anfassen abweicht und Rückmeldungen im Portal uneinheitlich aussehen.
 * Die Werte sind exakt die der bisherigen Kopien; das Erscheinungsbild ändert sich nicht.
 *
 * <p>Dazu {@link #showFailure} - die gemeinsame Meldung eines fehlgeschlagenen Schreibvorgangs
 * (Log + rote Meldung). Sie saß zunächst nur in {@code AbstractFormDialog}; seit der
 * Token-Verwaltung ({@code TerminalTokenDialog}) braucht sie auch ein Dialog, der bewusst nicht
 * von dort erbt, deshalb steht sie hier statisch statt als dritte Kopie derselben zwei Zeilen.
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

    /**
     * Meldet einen unerwarteten Fehler beim Ausführen einer Portal-Aktion.
     *
     * <p><b>Warum überhaupt</b>: das Portal registriert keinen eigenen Vaadin-{@code ErrorHandler}.
     * Eine Ausnahme, die aus einem Klick-Listener herausläuft, landet damit in Vaadins
     * {@code DefaultErrorHandler} - sie steht zwar im Server-Log, im Browser aber passiert
     * sichtbar nichts. Jeder schreibende Pfad fängt seine {@code RuntimeException} deshalb selbst
     * und meldet sie hierüber.
     *
     * <p><b>Warum nicht einfach {@code e.getMessage()} anhängen</b> (finale Review R3c, Issue
     * #92): die breiten {@code catch (RuntimeException)}-Zweige fangen neben den fachlich
     * erwarteten Ausnahmen auch Programmierfehler ab. Eine {@code NullPointerException} hat
     * typischerweise gar keine Meldung - der Administrator sah dann "... null" oder einen
     * abgeschnittenen Satz und der Auslöser war nirgends festgehalten. Deshalb: Ausnahme immer
     * ins Server-Log (dort steht der Stacktrace), im Portal die fachliche Meldung plus Detailtext
     * nur, wenn es einen gibt.
     *
     * @param source  die meldende Klasse (Ansicht/Dialog) - sie, nicht diese Utility-Klasse,
     *                steht als Logger-Name im Server-Log, damit dort erkennbar bleibt, WO der
     *                Fehler auftrat.
     */
    public static void showFailure(Class<?> source, String message, RuntimeException cause) {
        showFailure(source, message, cause, true);
    }

    /**
     * Wie {@link #showFailure(Class, String, RuntimeException)}, aber OHNE den Detailtext der
     * Ausnahme. Für Dialoge VOR dem Login ({@code PasswordForgotDialog}): dort säße sonst ein
     * anonymer Besucher vor der rohen Ausnahmemeldung und erführe je nach Fehler Mailserver-Namen
     * oder Datenbank-Constraints (finale Review R3c, Issue #92).
     */
    public static void showFailure(Class<?> source, String message, RuntimeException cause, boolean withDetail) {
        // Log-Text englisch wie im übrigen Backend.
        LoggerFactory.getLogger(source).error("Portal action failed: {}", message, cause);
        showError(withDetail ? failureText(message, cause.getMessage()) : failureText(message, null));
    }

    /**
     * Setzt die anzuzeigende Meldung zusammen. Als reine Funktion herausgezogen, damit der
     * Fallback-Fall ohne UI testbar ist.
     */
    static String failureText(String message, String detail) {
        return detail == null || detail.isBlank()
                ? message + " Unerwarteter Fehler - Details stehen im Server-Log."
                : message + " " + detail;
    }
}
