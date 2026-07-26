package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.slf4j.LoggerFactory;

/**
 * Das gemeinsame Gerüst der Formular-Dialoge des Portals (Anlegen/Bearbeiten von Benutzern,
 * Gruppen, Geräten, Programmen, Standorten sowie Guthaben, Passwort und Einstellungen):
 * Kopfzeile (Titel, modal, feste Breite) und Fußzeile aus "Abbrechen" plus primärer Aktion.
 *
 * <p>Herausgezogen im Rahmen der finalen Review (R3c, Issue #92) analog zu
 * {@code AbstractAdminListView}: die neun Dialoge bauten Kopf- und Fußzeile jeweils als eigene
 * Kopie nach, mit dem Risiko, dass eine Kopie beim nächsten Anfassen abweicht und Dialoge im
 * selben Portal unterschiedlich aussehen oder sich unterschiedlich bedienen lassen. Der Aufbau
 * ist exakt der bisherige (gleiche Reihenfolge Abbrechen/Aktion, gleiche Theme-Variante, gleicher
 * {@link HorizontalLayout}-Rahmen), das sichtbare Verhalten ändert sich nicht.
 *
 * <p><b>Nicht</b> für die rein lesenden Dialoge ({@code CreditHistoryDialog},
 * {@code LogViewerDialog}, {@code ExpiredExecutionsDialog}): die haben keine
 * Abbrechen/Primäraktion-Leiste, es bliebe nur die geerbte Kopfzeile. Sie tragen ihr
 * {@code setHeaderTitle}/{@code setModal}/{@code setWidth} deshalb bewusst weiter selbst.
 */
public abstract class AbstractFormDialog extends Dialog {

    /**
     * @param title Überschrift des Dialogs.
     * @param width Breite des Dialogs (CSS-Längenangabe, z. B. "45em").
     */
    protected AbstractFormDialog(String title, String width) {
        setHeaderTitle(title);
        setModal(true);
        setWidth(width);
    }

    /**
     * Die Überschrift eines Anlegen-/Bearbeiten-Dialogs, z. B. "Gerät bearbeiten" bzw.
     * "Gerät erstellen" - die Schreibweise des Alt-Portals, deshalb wörtlich beibehalten.
     */
    protected static String entityTitle(String entity, boolean editMode) {
        return entity + (editMode ? " bearbeiten" : " erstellen");
    }

    /**
     * Die Beschriftung der Primäraktion eines Anlegen-/Bearbeiten-Dialogs - ebenfalls 1:1 wie im
     * Alt-Portal.
     */
    protected static String saveCaption(boolean editMode) {
        return editMode ? "Speichern" : "Erstellen";
    }

    /**
     * Baut die Fußzeile: links "Abbrechen" (schließt den Dialog ohne Wirkung), rechts die
     * hervorgehobene Primäraktion.
     *
     * @return Die Schaltfläche der Primäraktion - für Dialoge, die sie noch nachjustieren (z. B.
     *         Doppelklick-Schutz auf geldbewegenden Aktionen, Issue #49).
     */
    protected Button addFooterActions(String submitCaption, Runnable onSubmit) {
        Button btnCancel = new Button("Abbrechen", e -> close());
        Button btnSubmit = new Button(submitCaption, e -> onSubmit.run());
        btnSubmit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new HorizontalLayout(btnCancel, btnSubmit));
        return btnSubmit;
    }

    /**
     * Setzt den Eingabefokus beim Öffnen auf das übergebene Feld (erst beim Öffnen, weil ein
     * noch nicht angezeigter Dialog keinen Fokus annehmen kann).
     */
    protected void focusOnOpen(Focusable<?> field) {
        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                field.focus();
            }
        });
    }

    /**
     * Meldet einen unerwarteten Fehler beim Ausführen der Dialog-Aktion.
     *
     * <p>Warum nicht einfach {@code e.getMessage()} anhängen (finale Review R3c, Issue #92): die
     * breiten {@code catch (RuntimeException)}-Zweige fangen neben den fachlich erwarteten
     * Ausnahmen auch Programmierfehler ab. Eine {@code NullPointerException} hat typischerweise
     * gar keine Meldung - der Administrator sah dann "... null" oder einen abgeschnittenen Satz
     * und der Auslöser war nirgends festgehalten. Deshalb: Ausnahme immer ins Server-Log (dort
     * steht der Stacktrace), im Portal die fachliche Meldung plus Detailtext nur, wenn es einen
     * gibt.
     */
    protected void showFailure(String message, RuntimeException cause) {
        // Logger je konkreter Dialogklasse (nicht der Basisklasse), damit im Log steht, WO der
        // Fehler auftrat; Log-Text englisch wie im übrigen Backend.
        LoggerFactory.getLogger(getClass()).error("Dialog action failed: {}", message, cause);
        Notifications.showError(failureText(message, cause.getMessage()));
    }

    /**
     * Wie {@link #showFailure(String, RuntimeException)}, aber OHNE den Detailtext der Ausnahme.
     * Für Dialoge VOR dem Login ({@code PasswordForgotDialog}): dort säße sonst ein anonymer
     * Besucher vor der rohen Ausnahmemeldung und erführe je nach Fehler Mailserver-Namen oder
     * Datenbank-Constraints (finale Review R3c, Issue #92).
     */
    protected void showFailure(String message, RuntimeException cause, boolean withDetail) {
        LoggerFactory.getLogger(getClass()).error("Dialog action failed: {}", message, cause);
        Notifications.showError(withDetail ? failureText(message, cause.getMessage())
                : failureText(message, null));
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
