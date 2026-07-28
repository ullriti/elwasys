package org.kabieror.elwasys.backend.ui.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/**
 * Die zwei rechnenden Teile der Dashboard-Gerätekarte (UI-Redesign v2, siehe
 * {@link AdminDashboardView}): der Füllgrad des Restzeit-Fortschrittsbalkens
 * ({@link AdminDashboardView#progressOf}) und die Übersetzung angeklickter Spaltenköpfe in die
 * Datenbank-Sortierung der Verlaufstabelle ({@link AdminDashboardView#toHistorySort}). Beide sind
 * genau dafür als reine Funktionen paketsichtbar herausgezogen (Muster
 * {@code Notifications#failureText}) - kein Spring, keine UI, kein Browser.
 */
class AdminDashboardViewTest {

    @Test
    void progressIsTheElapsedShareOfTheProgramsMaximumDuration() {
        assertThat(AdminDashboardView.progressOf(1800, 3600)).isEqualTo(0.5);
        assertThat(AdminDashboardView.progressOf(0, 3600)).isEqualTo(0.0);
        assertThat(AdminDashboardView.progressOf(3600, 3600)).isEqualTo(1.0);
    }

    @Test
    void anExecutionRunningPastItsMaximumDurationStopsAtAFullBar() {
        // Eine verspätete Endmeldung eines Terminals lässt die verstrichene Zeit über die
        // Höchstdauer hinauslaufen. Der Balken muss dann stehen bleiben statt überzulaufen (ein
        // Wert > 1 wäre für die ProgressBar außerhalb ihres Wertebereichs) - dieselbe Deckelung,
        // die ExecutionService#getPrice für die Abrechnung vornimmt.
        assertThat(AdminDashboardView.progressOf(7200, 3600)).isEqualTo(1.0);
    }

    @Test
    void aProgramWithoutAMaximumDurationShowsAFullBar() {
        // Ohne Höchstdauer gibt es keinen Bezugspunkt für einen Fortschritt; der Balken steht
        // voll, passend zur Restzeit 00:00:00, die daneben steht. Ein negativer Wert ist
        // fachlich derselbe Fall (und darf vor allem nicht durch Null teilen).
        assertThat(AdminDashboardView.progressOf(60, 0)).isEqualTo(1.0);
        assertThat(AdminDashboardView.progressOf(60, -1)).isEqualTo(1.0);
    }

    @Test
    void anExecutionThatHasNotStartedYetShowsAnEmptyBar() {
        // elapsedOf() liefert für eine Ausführung ohne Startzeitpunkt 0; eine in der Zukunft
        // liegende Startzeit (Uhren-Versatz zwischen Terminal und Backend) ergäbe einen negativen
        // Wert. Beides muss am leeren Balken enden, nicht an einem negativen Füllgrad.
        assertThat(AdminDashboardView.progressOf(0, 3600)).isEqualTo(0.0);
        assertThat(AdminDashboardView.progressOf(-30, 3600)).isEqualTo(0.0);
    }

    @Test
    void withoutAClickedColumnHeaderTheHistoryStaysNewestFirst() {
        assertThat(AdminDashboardView.toHistorySort(List.of()))
                .isEqualTo(Sort.by(Sort.Order.desc("start"), Sort.Order.desc("id")));
    }

    @Test
    void aClickedColumnHeaderSortsByItsPropertyAndKeepsTheStableSecondKey() {
        // Der Zweitschlüssel id DESC hängt an JEDER Sortierung, nicht nur an der Voreinstellung
        // (Issue #30): das Verlaufs-Grid lädt seitenweise, jede Seite ist eine eigene SQL-Abfrage.
        // Teilen sich zwei Ausführungen denselben Sortierwert - beim Sortieren nach Benutzer der
        // Regelfall -, dürfte Postgres sie ohne stabilen Zweitschlüssel über die Seiten hinweg
        // unterschiedlich anordnen; eine Zeile erschiene dann doppelt oder gar nicht.
        assertThat(AdminDashboardView.toHistorySort(
                List.of(new QuerySortOrder("user.name", SortDirection.ASCENDING))))
                .isEqualTo(Sort.by(Sort.Order.asc("user.name"), Sort.Order.desc("id")));

        assertThat(AdminDashboardView.toHistorySort(
                List.of(new QuerySortOrder("start", SortDirection.ASCENDING))))
                .as("Der umgekehrte Datums-Spaltenkopf ersetzt die Voreinstellung, statt sie zu ergänzen")
                .isEqualTo(Sort.by(Sort.Order.asc("start"), Sort.Order.desc("id")));

        assertThat(AdminDashboardView.toHistorySort(
                List.of(new QuerySortOrder("user.name", SortDirection.DESCENDING))))
                .isEqualTo(Sort.by(Sort.Order.desc("user.name"), Sort.Order.desc("id")));
    }

    @Test
    void aMultiColumnSortKeepsTheClickedOrderAndAppendsTheStableSecondKey() {
        assertThat(AdminDashboardView.toHistorySort(List.of(
                new QuerySortOrder("user.name", SortDirection.ASCENDING),
                new QuerySortOrder("start", SortDirection.DESCENDING))))
                .isEqualTo(Sort.by(Sort.Order.asc("user.name"), Sort.Order.desc("start"), Sort.Order.desc("id")));
    }
}
