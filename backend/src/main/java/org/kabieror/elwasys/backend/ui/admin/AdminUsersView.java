package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.events.CreditChangedEvent;
import org.kabieror.elwasys.backend.events.ExecutionChangedEvent;
import org.kabieror.elwasys.backend.events.UserChangedEvent;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PasswordResetService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.admin.dialog.CreditHistoryDialog;
import org.kabieror.elwasys.backend.ui.admin.dialog.CreditTopUpDialog;
import org.kabieror.elwasys.backend.ui.admin.dialog.ExpiredExecutionsDialog;
import org.kabieror.elwasys.backend.ui.admin.dialog.UserFormDialog;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Benutzerverwaltung (Phase 3 AP2/AP3, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../views/UsersView} (Alt-Portal, Testfälle P6-P8).
 *
 * <p>Guthaben wird in der Liste angezeigt ({@link CreditService#getCredit(UserEntity)}, 1:1
 * wie die Alt-Tabellenspalte "Guthaben") und kann über {@link CreditTopUpDialog} ("Guthaben
 * aufladen", fachlicher Nachfolger von {@code UserCreditWindow}, Testfall P8) verändert
 * werden; die vollständige, unveränderliche Buchungshistorie ist über
 * {@link CreditHistoryDialog} ("Umsätze ansehen", fachlicher Nachfolger von
 * {@code CreditAccountingWindow}) einsehbar - siehe {@code Portal/.../views/UsersView} für die
 * Alt-Anordnung dieser beiden Knöpfe neben "Bearbeiten"/"Löschen". <b>Seit Phase 3 AP4</b>
 * zusätzlich: der Admin-Passwort-Reset (Teil von {@link UserFormDialog}, siehe dort) und die
 * Warnung bei nicht abgerechneten Programmausführungen (Icon-Spalte, öffnet
 * {@link ExpiredExecutionsDialog} - fachlicher Nachfolger von {@code ExpiredExecutionsWindow}
 * bzw. der Warn-Icon-Logik in {@code Portal/.../views/UsersView#fillItemWithUserData}).
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn IRGENDEINE Session -
 * das Portal-UI oder (bei Guthaben-/Ausführungs-Ereignissen) ein Terminal über die REST-API -
 * einen Benutzer ändert, ein Guthaben bucht oder eine Ausführung ändert (letztere beiden
 * beeinflussen die angezeigten Spalten "Guthaben"/Warndreieck).
 */
@Route(value = "admin/users", layout = AdminLayout.class)
@PageTitle("Benutzer - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUsersView extends AbstractAdminListView<UserEntity> {

    private final UserService userService;
    private final UserGroupService userGroupService;
    private final CreditService creditService;
    private final ExecutionService executionService;
    private final PasswordResetService passwordResetService;
    private final String actingAdminName;

    /**
     * Guthaben je Benutzer-Id, in {@link #loadData()} gebündelt vorberechnet (Issue #30):
     * {@link #formatCredit} liest daraus, statt pro Grid-Zeile eine eigene Guthabenabfrage
     * auszulösen.
     */
    private Map<Integer, BigDecimal> creditByUserId = Map.of();

    public AdminUsersView(UserService userService, UserGroupService userGroupService, CreditService creditService,
            ExecutionService executionService, PasswordResetService passwordResetService, UiBroadcaster broadcaster,
            AuthenticationContext authenticationContext) {
        super("Benutzer", "admin-users-view", "Neu", broadcaster);
        this.userService = userService;
        this.userGroupService = userGroupService;
        this.creditService = creditService;
        this.executionService = executionService;
        this.passwordResetService = passwordResetService;
        this.actingAdminName = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getName).orElse(authenticationContext.getPrincipalName().orElse(""));

        initGrid();
    }

    @Override
    protected void configureColumns(Grid<UserEntity> grid) {
        grid.addColumn(UserEntity::getName).setHeader("Name").setSortable(true);
        grid.addColumn(UserEntity::getUsername).setHeader("Username").setSortable(true);
        grid.addColumn(u -> u.getGroup() == null ? "" : u.getGroup().getName()).setHeader("Gruppe")
                .setSortable(true);
        grid.addColumn(this::formatCardIds).setHeader("Kartennummer");
        // UI-Redesign v2 AP4: Guthaben ist im Prototyp sortierbar - der Vergleicher muss auf dem
        // tatsächlichen BigDecimal-Wert arbeiten, sonst sortiert Vaadin am formatierten
        // Währungstext (alphabetisch statt nach Betrag).
        grid.addColumn(this::formatCredit).setHeader("Guthaben").setSortable(true).setComparator(this::creditValue);
        grid.addComponentColumn(this::statusBadge).setHeader("Status");
        grid.addComponentColumn(this::expiredExecutionsWarning).setHeader("").setFlexGrow(0).setAutoWidth(true);
    }

    @Override
    protected boolean isRelevantChange(Object event) {
        // Guthaben- und Ausfuehrungs-Ereignisse aendern die angezeigten Spalten
        // "Guthaben"/Warndreieck - deshalb laedt die Liste auch bei ihnen neu.
        return event instanceof UserChangedEvent || event instanceof CreditChangedEvent
                || event instanceof ExecutionChangedEvent;
    }

    private String formatCardIds(UserEntity user) {
        String[] cardIds = Arrays.stream(user.getCardIds()).filter(v -> v != null && !v.isEmpty())
                .toArray(String[]::new);
        if (cardIds.length == 0) {
            return "";
        }
        return cardIds.length == 1 ? cardIds[0] : cardIds.length + " Karten";
    }

    private String formatCredit(UserEntity user) {
        return PortalFormats.currency(creditValue(user));
    }

    /**
     * Roher Guthabenwert für den Sortier-Vergleicher der Guthaben-Spalte (UI-Redesign v2 AP4) -
     * dieselbe Datenquelle wie {@link #formatCredit}, nur ungeformt.
     */
    private BigDecimal creditValue(UserEntity user) {
        // Issue #30: aus der in loadData() gebündelt geladenen Map statt einer Abfrage pro Zeile.
        return this.creditByUserId.getOrDefault(user.getId(), BigDecimal.ZERO);
    }

    /**
     * Suchtext der Zeile für das Filterfeld (UI-Redesign v2 AP4): deckt Name, Username, Gruppe,
     * Kartennummern und den Status-Badge-Text ab - dieselben Spalten wie {@link #configureColumns}
     * (ohne Guthaben, dessen Formatierung als Suchtext wenig sinnvolle Treffer liefern würde).
     */
    @Override
    protected String filterableText(UserEntity user) {
        String group = user.getGroup() == null ? "" : user.getGroup().getName();
        return String.join(" ", user.getName(), user.getUsername(), group, formatCardIds(user), statusLabel(user));
    }

    /**
     * Beschriftung der Status-Spalte. Eigene Methode, damit Badge-Text und Suchtext
     * ({@link #filterableText}) dieselbe Quelle haben - sonst fände der Filter eine später
     * geänderte Beschriftung nicht mehr, ohne dass es auffiele.
     */
    private static String statusLabel(UserEntity user) {
        return user.isBlocked() ? "Gesperrt" : "Aktiv";
    }

    private Span statusBadge(UserEntity user) {
        Span badge = new Span(statusLabel(user));
        badge.getElement().getThemeList().add("badge" + (user.isBlocked() ? " error" : " success"));
        return badge;
    }

    /**
     * 1:1-Portierung der Warn-Icon-Logik aus
     * {@code Portal/.../views/UsersView#fillItemWithUserData}: ein gesperrter Benutzer zeigt
     * (dort vorrangig) ein eigenes Sperr-Icon - das übernimmt hier bereits
     * {@link #statusBadge} als "Gesperrt"-Badge, deshalb zeigt diese Spalte NUR das
     * Warndreieck für nicht gesperrte Benutzer mit nicht abgerechneten Ausführungen (öffnet
     * {@link ExpiredExecutionsDialog}, fachlicher Nachfolger von
     * {@code ExpiredExecutionsWindow}); für alle anderen Benutzer bleibt die Zelle leer (1:1
     * wie das Alt-"normale Benutzer"-Icon, das keine Aktion auslöst).
     */
    private Span expiredExecutionsWarning(UserEntity user) {
        if (user.isBlocked() || !this.executionService.hasExpiredExecutions(user)) {
            return new Span();
        }
        Button btn = new Button(new Icon(VaadinIcon.WARNING));
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR);
        btn.setTooltipText("Es gibt nicht abgerechnete Programmausführungen");
        btn.addClickListener(e -> openExpiredExecutionsDialog(user));
        Span wrapper = new Span(btn);
        return wrapper;
    }

    @Override
    protected HorizontalLayout actionButtons(UserEntity user) {
        HorizontalLayout buttons = super.actionButtons(user);

        Button btnCredit = new Button(new Icon(VaadinIcon.EURO));
        btnCredit.setTooltipText("Guthaben aufladen");
        btnCredit.addClickListener(e -> openCreditTopUpDialog(user));

        Button btnCreditHistory = new Button(new Icon(VaadinIcon.RECORDS));
        btnCreditHistory.setTooltipText("Umsätze ansehen");
        btnCreditHistory.addClickListener(e -> openCreditHistoryDialog(user));

        // Reihenfolge 1:1 wie im Alt-Portal: Bearbeiten, Guthaben, Umsätze, Löschen - deshalb
        // werden die zwei zusätzlichen Knöpfe VOR dem Löschen-Knopf der Basisklasse eingefügt.
        buttons.addComponentAtIndex(1, btnCredit);
        buttons.addComponentAtIndex(2, btnCreditHistory);
        return buttons;
    }

    @Override
    protected void openCreateDialog() {
        new UserFormDialog(this.userService, this.userGroupService, this.passwordResetService, null, this::loadData)
                .open();
    }

    @Override
    protected void openEditDialog(UserEntity user) {
        new UserFormDialog(this.userService, this.userGroupService, this.passwordResetService, user, this::loadData)
                .open();
    }

    private void openExpiredExecutionsDialog(UserEntity user) {
        new ExpiredExecutionsDialog(this.executionService, user, this::loadData).open();
    }

    private void openCreditTopUpDialog(UserEntity user) {
        new CreditTopUpDialog(this.creditService, user, this.actingAdminName, this::loadData).open();
    }

    private void openCreditHistoryDialog(UserEntity user) {
        new CreditHistoryDialog(this.creditService, user).open();
    }

    @Override
    protected void delete(UserEntity user) {
        // Soft-Delete (Issue #39): der Benutzer bleibt als Beleg erhalten, ist aber nicht mehr
        // aktiv - eine EntityInUseException gibt es hier deshalb nicht.
        this.userService.delete(user);
    }

    @Override
    protected String deleteDialogTitle() {
        return "Benutzer löschen";
    }

    @Override
    protected String deleteDialogQuestion(UserEntity user) {
        return "Möchten Sie diesen Benutzer wirklich löschen? " + user.getName();
    }

    @Override
    protected List<UserEntity> findAll() {
        return this.userService.findAllActive();
    }

    @Override
    protected void loadData() {
        List<UserEntity> users = findAll();
        // Issue #30: Guthaben aller Benutzer in zwei Abfragen bündeln, statt pro Grid-Zeile
        // (formatCredit) eine eigene Guthabenabfrage auszulösen.
        this.creditByUserId = this.creditService.getCredits(users);
        // setGridItems() statt getGrid().setItems(...) (UI-Redesign v2 AP4): wendet den aktuell
        // eingegebenen Filtertext nach dem Neuladen erneut an.
        setGridItems(users);
    }
}
