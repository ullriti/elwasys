package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

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
 * {@code setHeaderTitle}/{@code setModal}/{@code setWidth} deshalb bewusst weiter selbst - das
 * Schließen-Kreuz holen sie sich über {@link #addCloseButton(Dialog)}.
 *
 * <p><b>UI-Redesign v2</b> (siehe docs/specs/0002-ui-design/v2/MAPPING.md und
 * docs/kb/05-migration-plan.md): Kopfzeile mit Schließen-Kreuz, feststehender Kopf/Fuß mit
 * scrollendem Rumpf ({@link #addToBody}) und Abschnittsgliederung ({@link #addSection}) - eine
 * reine Darstellungsänderung, Felder, Bindung und Validierung der Dialoge bleiben unberührt.
 */
public abstract class AbstractFormDialog extends Dialog {

    /**
     * Obergrenze der Dialoghöhe (UI-Redesign v2). Ohne sie wächst der Dialog mit seinem Inhalt,
     * und es gäbe im Rumpf nichts zu scrollen - Kopf und Fuß könnten also gar nicht feststehen.
     * Der Wert deckelt gegen den sichtbaren Bereich und lässt oben/unten Luft zum Rand.
     */
    private static final String MAX_HEIGHT = "min(46rem, 90vh)";

    /**
     * Der scrollende Inhaltsbereich zwischen Kopf- und Fußzeile. Alle Unterklassen hängen ihren
     * Inhalt über {@link #addToBody} bzw. {@link #addSection} hier ein - bewusst kein
     * Mischbetrieb mit dem geerbten {@code Dialog#add}, sonst säßen einzelne Felder außerhalb
     * des scrollenden Bereichs.
     */
    private final Div body = new Div();

    /**
     * @param title Überschrift des Dialogs.
     * @param width Breite des Dialogs (CSS-Längenangabe, z. B. "45em").
     */
    protected AbstractFormDialog(String title, String width) {
        setHeaderTitle(title);
        setModal(true);
        setWidth(width);
        setMaxHeight(MAX_HEIGHT);

        addCloseButton(this);

        this.body.addClassName("dialog-body");
        add(this.body);
    }

    /**
     * Setzt das Schließen-Kreuz in die Kopfzeile eines Dialogs (UI-Redesign v2).
     *
     * <p>Statisch und mit dem {@link Dialog} als Parameter statt als Instanzmethode, weil die
     * rein lesenden Dialoge (siehe Klassen-Javadoc) bewusst NICHT von dieser Klasse erben,
     * dasselbe Kreuz aber trotzdem tragen sollen: drei Kopien desselben Knopfs wären genau die
     * Abweichung, die diese Basisklasse verhindern soll.
     */
    public static void addCloseButton(Dialog dialog) {
        Button btnClose = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> dialog.close());
        btnClose.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        btnClose.addClassName("dialog-close");
        // Ein Knopf ohne Beschriftung hat keinen zugänglichen Namen - ohne aria-label wäre er
        // weder mit dem Screenreader noch aus der Playwright-Suite heraus adressierbar.
        // Bewusst NICHT "Schließen": der LogViewerDialog trägt in seiner Fußzeile eine
        // Schaltfläche genau dieses Namens, es gäbe dann zwei gleichnamige Bedienelemente im
        // selben Dialog - für den Screenreader nicht unterscheidbar, für eine Suche nach dem
        // Namen mehrdeutig.
        btnClose.setAriaLabel("Dialog schließen");
        dialog.getHeader().add(btnClose);
    }

    /**
     * Hängt Inhalt in den scrollenden Rumpf des Dialogs (UI-Redesign v2) - der Ersatz für das
     * geerbte {@code Dialog#add} in allen Unterklassen.
     */
    protected void addToBody(Component... components) {
        this.body.add(components);
    }

    /**
     * Legt einen Abschnitt im Rumpf an: Versalien-Überschrift plus ein eigenes
     * {@link FormLayout}, zusammengefasst in einem {@code Div} (UI-Redesign v2, Gliederung der
     * Geräte-/Programm-Dialoge in Stammdaten/Gateway-Anbindung/...).
     *
     * <p>Die Überschrift ist bewusst ein {@link Span} und keine echte Überschrift: sie ist ein
     * Gliederungslabel, kein Feldlabel und kein Bedienelement - so bleibt sie aus der
     * Rollen-/Label-Suche der E2E-Tests heraus.
     *
     * @return Das {@link FormLayout} des Abschnitts, in das der Aufrufer seine Felder hängt.
     */
    protected FormLayout addSection(String title) {
        Span heading = new Span(title);
        heading.addClassName("dialog-section-title");

        FormLayout form = new FormLayout();
        // Wie bisher in den mehrspaltigen Dialogen: eine Spalte auf schmalen Fenstern, ab 30em
        // zwei - die Abschnittsgliederung ändert daran nichts.
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30em", 2));

        Div section = new Div(heading, form);
        section.addClassName("dialog-section");
        addToBody(section);
        return form;
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
     * Meldet einen unerwarteten Fehler beim Ausführen der Dialog-Aktion - siehe
     * {@link Notifications#showFailure(Class, String, RuntimeException)}, dort steht auch die
     * Begründung. {@code getClass()} ist die konkrete Dialogklasse: im Server-Log steht damit,
     * WO der Fehler auftrat.
     */
    protected void showFailure(String message, RuntimeException cause) {
        Notifications.showFailure(getClass(), message, cause);
    }

    /**
     * Wie {@link #showFailure(String, RuntimeException)}, aber OHNE den Detailtext der Ausnahme -
     * siehe {@link Notifications#showFailure(Class, String, RuntimeException, boolean)}.
     */
    protected void showFailure(String message, RuntimeException cause, boolean withDetail) {
        Notifications.showFailure(getClass(), message, cause, withDetail);
    }
}
