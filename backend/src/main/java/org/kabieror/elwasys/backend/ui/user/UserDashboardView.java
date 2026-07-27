package org.kabieror.elwasys.backend.ui.user;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.events.CreditChangedEvent;
import org.kabieror.elwasys.backend.events.DomainEvent;
import org.kabieror.elwasys.backend.events.ExecutionChangedEvent;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.component.ListFilterField;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Benutzer-Dashboard (Phase 3 AP3, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../views/UsersDashboardView} (Alt-Portal, Testfall P15: "Guthaben"/
 * "Übersicht" sichtbar - "Übersicht" ist bereits der Menüpunkt in {@link UserLayout}, "Guthaben"
 * die Beschriftung der Kachel in dieser View). Zeigt das eigene Guthaben, die letzte eigene
 * Einzahlung und die vollständige eigene Buchungshistorie ("Buchungen"-Tabelle, 1:1 wie im
 * Alt-Portal).
 *
 * <p><b>Datenisolation</b>: der angezeigte Benutzer kommt ausschließlich aus dem
 * {@link ElwasysUserPrincipal} der aktuellen Session (nicht aus einem Pfad-/Query-Parameter) -
 * ein Nicht-Administrator kann über diese View also strukturell nur die eigenen Daten sehen,
 * nie die eines anderen Benutzers.
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * das eigene Guthaben ändert sich nicht nur durch eigenes Zutun (z.B. Admin lädt Guthaben in
 * einer anderen Session auf, oder ein Terminal meldet über die REST-API das Ende einer
 * Programmausführung dieses Benutzers) - die View meldet sich daher beim {@link UiBroadcaster}
 * an und aktualisiert Guthaben-Kachel, "Letzte Einzahlung"-Kachel und Buchungstabelle bei jedem
 * {@link CreditChangedEvent}/{@link ExecutionChangedEvent} des EIGENEN Benutzers (andere
 * Benutzer betreffen diese Session dank der Datenisolation ohnehin nicht).
 *
 * <p><b>Seit dem UI-Redesign v2</b> (docs/specs/0002-ui-design/v2/MAPPING.md, "Benutzerbereich",
 * siehe docs/kb/05-migration-plan.md): die Buchungstabelle ist sortier- und filterbar - der
 * Aufbau der Seite bleibt sonst unverändert.
 */
@Route(value = "user", layout = UserLayout.class)
@PageTitle("Übersicht - Waschportal")
@RolesAllowed("USER")
public class UserDashboardView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.GERMANY);

    private final CreditService creditService;
    private final UiBroadcaster broadcaster;
    private final UserEntity user;

    private final Span creditValueLabel = new Span();
    private final Span lastInpaymentValueLabel = new Span();
    private final Grid<CreditAccountingEntryEntity> grid = new Grid<>();

    /**
     * Freitextfilter über der Buchungstabelle (UI-Redesign v2): eine Buchungshistorie wächst mit
     * jeder Einzahlung und jedem Waschgang, gesucht wird darin aber fast immer nach einem
     * konkreten Datum, Betrag oder Buchungstext. Es ist dieselbe Komponente wie über den
     * Admin-Listen - ein Portal, ein Suchfeld mit einer Suchsemantik.
     */
    private final ListFilterField<CreditAccountingEntryEntity> filterField =
            new ListFilterField<>("Buchungen filtern");

    private Registration broadcasterRegistration;

    public UserDashboardView(AuthenticationContext authenticationContext, UserService userService,
            CreditService creditService, UiBroadcaster broadcaster) {
        this.creditService = creditService;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName("user-dashboard-view");

        add(new H2("Übersicht"));

        Optional<UserEntity> currentUser = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getUserId).flatMap(userService::findById);

        if (currentUser.isEmpty()) {
            this.user = null;
            add(new Span("Ihr Benutzerkonto konnte nicht geladen werden."));
            return;
        }
        this.user = currentUser.get();

        HorizontalLayout topPanels = new HorizontalLayout();
        topPanels.addClassName("dashboard-top-panels");
        topPanels.add(buildSparkTile("Guthaben", this.creditValueLabel));
        topPanels.add(buildSparkTile("Letzte Einzahlung", this.lastInpaymentValueLabel));
        add(topPanels);

        // Überschrift und Filter in einer Zeile - dieselbe Toolbar-Anordnung wie in den
        // Admin-Listen (AbstractAdminListView), damit das Portal an beiden Stellen gleich zu
        // bedienen ist.
        H3 bookingsHeading = new H3("Buchungen");
        HorizontalLayout bookingsToolbar = new HorizontalLayout(bookingsHeading, this.filterField);
        bookingsToolbar.addClassName("list-toolbar");
        bookingsToolbar.setWidthFull();
        bookingsToolbar.setFlexGrow(1, bookingsHeading);
        bookingsToolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        add(bookingsToolbar);
        // Sortierung: die Spalten zeigen formatierte Texte ("12.03.25, 14:07", "1,50 €") - ohne
        // eigenen Comparator würde das Grid genau diese Zeichenketten alphabetisch ordnen und
        // damit weder chronologisch noch numerisch sortieren. Deshalb je Spalte der Vergleich
        // auf dem Rohwert.
        this.grid.addColumn(e -> PortalFormats.dateTime(e.getDate())).setHeader("Datum").setFlexGrow(0)
                .setWidth("12rem").setComparator(CreditAccountingEntryEntity::getDate).setSortable(true);
        // Tabellarische Ziffern auf der Betragsspalte (setClassNameGenerator ist in Vaadin 24.10
        // zugunsten von Shadow-DOM-Parts als veraltet markiert - hier bewusst weiter verwendet,
        // weil das Portal-Theme die Zellen über CSS-Klassen anspricht, nicht über ::part).
        this.grid.addColumn(e -> PortalFormats.currency(e.getAmount())).setHeader("Betrag").setFlexGrow(0)
                // Breiten in rem, nicht em, und 9 statt 8: der Versalien-Kopf des Designs v2
                // steht in 0.75rem - eine em-Breite ergäbe im Kopf schmalere Spalten als im
                // Körper (siehe Kommentar in portal-theme.css), und "BETRAG" braucht etwas
                // mehr Platz als die vorherige Normalschrift.
                .setWidth("9rem").setComparator(CreditAccountingEntryEntity::getAmount).setSortable(true)
                .setClassNameGenerator(e -> "tabular-nums");
        this.grid.addColumn(CreditAccountingEntryEntity::getDescription).setHeader("Buchungstext")
                .setComparator(CreditAccountingEntryEntity::getDescription).setSortable(true);
        this.grid.setSizeFull();
        add(this.grid);
        setFlexGrow(1, this.grid);

        // Erst binden, dann laden: refresh() legt den Filter danach auf jeden neuen Datenbestand.
        this.filterField.bindTo(this.grid, UserDashboardView::filterableText);

        refresh();
    }

    /**
     * Suchtext einer Buchung für das Filterfeld (UI-Redesign v2): die drei angezeigten Spalten,
     * jeweils über dieselbe Formatierung wie die Zelle selbst - gesucht wird damit genau das, was
     * der Benutzer vor sich sieht.
     */
    private static String filterableText(CreditAccountingEntryEntity entry) {
        return String.join(" ", PortalFormats.dateTime(entry.getDate()),
                PortalFormats.currency(entry.getAmount()),
                entry.getDescription() == null ? "" : entry.getDescription());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (this.user == null) {
            return;
        }
        Integer ownUserId = this.user.getId();
        this.broadcasterRegistration = this.broadcaster.register(attachEvent.getUI(), event -> {
            if (concernsOwnUser(event, ownUserId)) {
                refresh();
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (this.broadcasterRegistration != null) {
            this.broadcasterRegistration.remove();
            this.broadcasterRegistration = null;
        }
        super.onDetach(detachEvent);
    }

    private static boolean concernsOwnUser(DomainEvent event, Integer ownUserId) {
        return switch (event) {
            case CreditChangedEvent(Integer userId) -> ownUserId.equals(userId);
            case ExecutionChangedEvent(Integer executionId, Integer deviceId, Integer userId) -> ownUserId.equals(
                    userId);
            default -> false;
        };
    }

    /**
     * Aktualisiert Guthaben-Kachel, "Letzte Einzahlung"-Kachel und Buchungstabelle - sowohl
     * beim erstmaligen Aufbau als auch bei einem Live-Update (Phase 3 AP5, siehe
     * Klassen-Javadoc).
     */
    private void refresh() {
        this.creditValueLabel.setText(PortalFormats.currency(this.creditService.getCredit(this.user)));
        this.lastInpaymentValueLabel.setText(
                this.creditService.getLastInpayment(this.user).map(e -> e.getDate().format(DATE_FORMAT)).orElse("-"));
        this.grid.setItems(this.creditService.getAccountingEntries(this.user));
        // Ein Live-Update darf einen gesetzten Filter nicht stillschweigend fallen lassen - sonst
        // stünde plötzlich wieder die volle Historie da, während im Feld noch der Suchbegriff steht.
        this.filterField.reapply();
    }

    private static VerticalLayout buildSparkTile(String caption, Span valueLabel) {
        VerticalLayout tile = new VerticalLayout();
        tile.addClassName("dashboard-spark");
        tile.setPadding(false);
        tile.setSpacing(false);
        valueLabel.addClassName("dashboard-spark-value");
        Span captionLabel = new Span(caption);
        captionLabel.addClassName("dashboard-spark-caption");
        tile.add(valueLabel, captionLabel);
        return tile;
    }
}
