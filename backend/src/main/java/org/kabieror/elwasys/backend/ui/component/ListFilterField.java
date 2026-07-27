package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.SerializableFunction;
import java.util.Locale;
import java.util.Objects;

/**
 * Das Freitext-Suchfeld über einer Portal-Tabelle (UI-Redesign v2, siehe
 * docs/specs/0002-ui-design/v2/MAPPING.md, Abschnitt "Listen").
 *
 * <p>Warum als eigene Komponente: das Feld war nach dem Redesign an drei Stellen einzeln
 * nachgebaut ({@code AbstractAdminListView}, {@code AdminOfflineIncidentsView},
 * {@code UserDashboardView}) - mit gleichem Aussehen, aber ZWEI verschiedenen Suchsemantiken
 * (Suche über einen Suchtext je Zeile vs. Suche über die einzelnen Anzeigetexte). Damit verhielt
 * sich dasselbe Suchfeld im selben Portal unterschiedlich; ein Redesign-Review hat das als
 * blockierend eingestuft. Hier gibt es nun genau eine Ausstattung und genau eine Semantik.
 *
 * <p>Gefiltert wird <b>clientnah</b> auf dem bereits geladenen {@link ListDataProvider} des Grids:
 * alle drei Aufrufstellen laden ihre Zeilen vollständig (eager), ein Nachreichen des Filters in
 * eine Datenbankabfrage entfällt deshalb.
 *
 * <p>Benutzung:
 * <pre>{@code
 * ListFilterField<DeviceEntity> filter = new ListFilterField<>("Geräte filtern");
 * filter.bindTo(grid, this::filterableText);
 * // ... und nach jedem grid.setItems(...):
 * filter.reapply();
 * }</pre>
 *
 * @param <T> Der Zeilentyp der gefilterten Tabelle.
 */
public class ListFilterField<T> extends TextField {

    /**
     * Geschützte Leerzeichen, die in den Anzeigetexten vorkommen, aber auf keiner Tastatur
     * liegen: {@link PortalFormats#currency} liefert (über {@code NumberFormat} in deutscher
     * Schreibweise) ein U+00A0 vor dem Währungszeichen, andere Formate ein schmales U+202F. Wer
     * "1,50 €" mit der normalen Leertaste tippt, fände sonst nichts, obwohl genau das auf dem
     * Bildschirm steht.
     */
    private static final char[] NO_BREAK_SPACES = {'\u00A0', '\u202F'};

    private Grid<T> grid;

    private SerializableFunction<T, String> searchableText;

    /**
     * @param ariaLabel Zugängliche Beschriftung des Feldes, z. B. "Geräte filtern". Der
     *                  Platzhalter allein genügt dafür nicht: er ist als zugänglicher Name nur
     *                  das letzte Mittel und verschwindet, sobald der Benutzer tippt.
     */
    public ListFilterField(String ariaLabel) {
        addClassName("list-filter");
        setPlaceholder("Suchen");
        setAriaLabel(ariaLabel);
        setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        setClearButtonVisible(true);
        // LAZY statt EAGER: filtert erst nach einer kurzen Tippause, statt bei jedem Zeichen eine
        // Serverrunde auszulösen.
        setValueChangeMode(ValueChangeMode.LAZY);
        addValueChangeListener(e -> reapply());
    }

    /**
     * Verbindet das Feld mit der zu filternden Tabelle.
     *
     * @param grid           Die Tabelle, deren {@link ListDataProvider} gefiltert wird.
     * @param searchableText Der durchsuchbare Text einer Zeile - üblicherweise die sichtbaren
     *                       Textspalten, mit Leerzeichen verbunden. Groß-/Kleinschreibung und
     *                       Leerraum spielen keine Rolle, {@link #normalize(String)} bereitet
     *                       beide Seiten des Vergleichs auf.
     */
    public void bindTo(Grid<T> grid, SerializableFunction<T, String> searchableText) {
        this.grid = Objects.requireNonNull(grid);
        this.searchableText = Objects.requireNonNull(searchableText);
        reapply();
    }

    /**
     * Legt den aktuellen Suchbegriff (erneut) auf die Tabelle. Öffentlich, weil
     * {@code grid.setItems(...)} einen NEUEN, ungefilterten {@link ListDataProvider} anlegt: jede
     * Ansicht, die ihre Zeilen neu setzt - beim erstmaligen Aufbau ebenso wie bei einem
     * Live-Update über den {@code UiBroadcaster} - muss danach reapply() rufen, sonst stünde
     * plötzlich wieder die volle Liste da, während im Feld noch der Suchbegriff steht.
     *
     * <p>Ein leerer Suchbegriff (auch nach Trimmen) löscht einen zuvor gesetzten Filter wieder.
     */
    public void reapply() {
        ListDataProvider<T> dataProvider = listDataProvider();
        if (dataProvider == null) {
            return;
        }
        String term = normalize(getValue());
        if (term.isEmpty()) {
            dataProvider.clearFilters();
        } else {
            dataProvider.setFilter(item -> matches(this.searchableText.apply(item), term));
        }
    }

    @SuppressWarnings("unchecked")
    private ListDataProvider<T> listDataProvider() {
        if (this.grid == null) {
            return null;
        }
        // Der Parametertyp ist wegen Typlöschung nicht prüfbar (kein "instanceof
        // ListDataProvider<T>") - deshalb Prüfung auf den Rohtyp und anschließend ungeprüfter
        // Cast, wie überall sonst im Umgang mit Vaadins generischen DataProvider-Typen üblich.
        DataProvider<T, ?> dataProvider = this.grid.getDataProvider();
        if (dataProvider instanceof ListDataProvider) {
            return (ListDataProvider<T>) dataProvider;
        }
        return null;
    }

    /**
     * Das Filter-Prädikat einer einzelnen Zeile. Als reine Funktion herausgezogen (wie
     * {@code AbstractFormDialog#failureText}), damit die Suchsemantik des Portals ohne Browser
     * prüfbar ist - genau dieser Vergleich läuft auch in {@link #reapply()}.
     *
     * @param searchableText  Der rohe Suchtext der Zeile; {@code null} trifft nie.
     * @param normalizedTerm  Der bereits über {@link #normalize(String)} aufbereitete Suchbegriff.
     */
    static boolean matches(String searchableText, String normalizedTerm) {
        return normalize(searchableText).contains(normalizedTerm);
    }

    /**
     * Die Vergleichsform eines Textes: ohne geschützte Leerzeichen (siehe
     * {@link #NO_BREAK_SPACES}), ohne umgebenden Leerraum und kleingeschrieben. {@code null} wird
     * zum leeren Text.
     */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value;
        for (char noBreakSpace : NO_BREAK_SPACES) {
            normalized = normalized.replace(noBreakSpace, ' ');
        }
        // Erst ersetzen, dann trimmen: ein führendes geschütztes Leerzeichen soll ebenfalls fallen.
        return normalized.trim().toLowerCase(Locale.GERMANY);
    }
}
