package org.kabieror.elwasys.backend.ui.user;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
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
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.UserService;

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
 */
@Route(value = "user", layout = UserLayout.class)
@PageTitle("Übersicht - Waschportal")
@RolesAllowed("USER")
public class UserDashboardView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.GERMANY);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.SHORT).withLocale(Locale.GERMANY);

    public UserDashboardView(AuthenticationContext authenticationContext, UserService userService,
            CreditService creditService) {
        setSizeFull();
        addClassName("user-dashboard-view");

        add(new H2("Übersicht"));

        Optional<UserEntity> currentUser = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getUserId).flatMap(userService::findById);

        if (currentUser.isEmpty()) {
            add(new Span("Ihr Benutzerkonto konnte nicht geladen werden."));
            return;
        }
        UserEntity user = currentUser.get();

        HorizontalLayout topPanels = new HorizontalLayout();
        topPanels.addClassName("dashboard-top-panels");
        topPanels.add(buildSparkTile("Guthaben", formatCurrency(creditService.getCredit(user))));
        String lastInpayment = creditService.getLastInpayment(user).map(e -> e.getDate().format(DATE_FORMAT))
                .orElse("-");
        topPanels.add(buildSparkTile("Letzte Einzahlung", lastInpayment));
        add(topPanels);

        add(new H3("Buchungen"));
        Grid<CreditAccountingEntryEntity> grid = new Grid<>();
        grid.addColumn(e -> e.getDate().format(DATE_TIME_FORMAT)).setHeader("Datum").setFlexGrow(0).setWidth("12em");
        grid.addColumn(e -> formatCurrency(e.getAmount())).setHeader("Betrag").setFlexGrow(0).setWidth("8em");
        grid.addColumn(CreditAccountingEntryEntity::getDescription).setHeader("Buchungstext");
        grid.setItems(creditService.getAccountingEntries(user));
        grid.setSizeFull();
        add(grid);
        setFlexGrow(1, grid);
    }

    private static VerticalLayout buildSparkTile(String caption, String value) {
        VerticalLayout tile = new VerticalLayout();
        tile.addClassName("dashboard-spark");
        tile.setPadding(false);
        tile.setSpacing(false);
        Span valueLabel = new Span(value);
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
