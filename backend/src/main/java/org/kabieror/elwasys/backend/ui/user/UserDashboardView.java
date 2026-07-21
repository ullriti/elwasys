package org.kabieror.elwasys.backend.ui.user;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.text.NumberFormat;
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
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Benutzer-Dashboard (Phase 3 AP3, siehe kb/05-migration-plan.md) - fachlicher Nachfolger von
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
 * <p><b>Seit Phase 3 AP5</b> (siehe kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * das eigene Guthaben ändert sich nicht nur durch eigenes Zutun (z.B. Admin lädt Guthaben in
 * einer anderen Session auf, oder ein Terminal meldet über die REST-API das Ende einer
 * Programmausführung dieses Benutzers) - die View meldet sich daher beim {@link UiBroadcaster}
 * an und aktualisiert Guthaben-Kachel, "Letzte Einzahlung"-Kachel und Buchungstabelle bei jedem
 * {@link CreditChangedEvent}/{@link ExecutionChangedEvent} des EIGENEN Benutzers (andere
 * Benutzer betreffen diese Session dank der Datenisolation ohnehin nicht).
 */
@Route(value = "user", layout = UserLayout.class)
@PageTitle("Übersicht - Waschportal")
@RolesAllowed("USER")
public class UserDashboardView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.GERMANY);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.SHORT).withLocale(Locale.GERMANY);

    private final CreditService creditService;
    private final UiBroadcaster broadcaster;
    private final UserEntity user;

    private final Span creditValueLabel = new Span();
    private final Span lastInpaymentValueLabel = new Span();
    private final Grid<CreditAccountingEntryEntity> grid = new Grid<>();

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

        add(new H3("Buchungen"));
        this.grid.addColumn(e -> e.getDate().format(DATE_TIME_FORMAT)).setHeader("Datum").setFlexGrow(0)
                .setWidth("12em");
        this.grid.addColumn(e -> formatCurrency(e.getAmount())).setHeader("Betrag").setFlexGrow(0).setWidth("8em");
        this.grid.addColumn(CreditAccountingEntryEntity::getDescription).setHeader("Buchungstext");
        this.grid.setSizeFull();
        add(this.grid);
        setFlexGrow(1, this.grid);

        refresh();
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
        this.creditValueLabel.setText(formatCurrency(this.creditService.getCredit(this.user)));
        this.lastInpaymentValueLabel.setText(
                this.creditService.getLastInpayment(this.user).map(e -> e.getDate().format(DATE_FORMAT)).orElse("-"));
        this.grid.setItems(this.creditService.getAccountingEntries(this.user));
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

    private static String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount);
    }
}
