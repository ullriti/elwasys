package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.text.NumberFormat;
import java.util.Locale;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.admin.dialog.CreditHistoryDialog;
import org.kabieror.elwasys.backend.ui.admin.dialog.CreditTopUpDialog;
import org.kabieror.elwasys.backend.ui.admin.dialog.UserFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;

/**
 * Benutzerverwaltung (Phase 3 AP2/AP3, siehe kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../views/UsersView} (Alt-Portal, Testfälle P6-P8).
 *
 * <p>Guthaben wird in der Liste angezeigt ({@link CreditService#getCredit(UserEntity)}, 1:1
 * wie die Alt-Tabellenspalte "Guthaben") und kann über {@link CreditTopUpDialog} ("Guthaben
 * aufladen", fachlicher Nachfolger von {@code UserCreditWindow}, Testfall P8) verändert
 * werden; die vollständige, unveränderliche Buchungshistorie ist über
 * {@link CreditHistoryDialog} ("Umsätze ansehen", fachlicher Nachfolger von
 * {@code CreditAccountingWindow}) einsehbar - siehe {@code Portal/.../views/UsersView} für die
 * Alt-Anordnung dieser beiden Knöpfe neben "Bearbeiten"/"Löschen". Nicht Teil dieser View
 * (siehe kb/05-migration-plan.md Phase-3-Roadmap, AP4): der Admin-Passwort-Reset und die
 * Warnung bei nicht abgerechneten Programmausführungen ({@code ExpiredExecutionsWindow},
 * Alt-Icon-Spalte).
 */
@Route(value = "admin/users", layout = AdminLayout.class)
@PageTitle("Benutzer - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUsersView extends VerticalLayout {

    private final UserService userService;
    private final UserGroupService userGroupService;
    private final CreditService creditService;
    private final String actingAdminName;

    private final Grid<UserEntity> grid = new Grid<>();

    public AdminUsersView(UserService userService, UserGroupService userGroupService, CreditService creditService,
            AuthenticationContext authenticationContext) {
        this.userService = userService;
        this.userGroupService = userGroupService;
        this.creditService = creditService;
        this.actingAdminName = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getName).orElse(authenticationContext.getPrincipalName().orElse(""));

        setSizeFull();
        addClassName("admin-users-view");

        H2 title = new H2("Benutzer");
        Button btnNew = new Button("Neu", new Icon(VaadinIcon.PLUS), e -> openCreateDialog());
        btnNew.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, btnNew);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, title);
        toolbar.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

        configureGrid();

        add(toolbar, this.grid);
        setFlexGrow(1, this.grid);

        loadData();
    }

    private void configureGrid() {
        this.grid.setSizeFull();

        this.grid.addColumn(UserEntity::getName).setHeader("Name").setSortable(true);
        this.grid.addColumn(UserEntity::getUsername).setHeader("Username").setSortable(true);
        this.grid.addColumn(u -> u.getGroup() == null ? "" : u.getGroup().getName()).setHeader("Gruppe")
                .setSortable(true);
        this.grid.addColumn(this::formatCardIds).setHeader("Kartennummer");
        this.grid.addColumn(this::formatCredit).setHeader("Guthaben");
        this.grid.addComponentColumn(this::statusBadge).setHeader("Status");
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("190px");
    }

    private String formatCardIds(UserEntity user) {
        String[] cardIds = java.util.Arrays.stream(user.getCardIds()).filter(v -> v != null && !v.isEmpty())
                .toArray(String[]::new);
        if (cardIds.length == 0) {
            return "";
        }
        return cardIds.length == 1 ? cardIds[0] : cardIds.length + " Karten";
    }

    private String formatCredit(UserEntity user) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(this.creditService.getCredit(user));
    }

    private Span statusBadge(UserEntity user) {
        Span badge = new Span(user.isBlocked() ? "Gesperrt" : "Aktiv");
        badge.getElement().getThemeList().add("badge" + (user.isBlocked() ? " error" : " success"));
        return badge;
    }

    private HorizontalLayout actionButtons(UserEntity user) {
        Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
        btnEdit.setTooltipText("Bearbeiten");
        btnEdit.addClickListener(e -> openEditDialog(user));

        Button btnCredit = new Button(new Icon(VaadinIcon.EURO));
        btnCredit.setTooltipText("Guthaben aufladen");
        btnCredit.addClickListener(e -> openCreditTopUpDialog(user));

        Button btnCreditHistory = new Button(new Icon(VaadinIcon.RECORDS));
        btnCreditHistory.setTooltipText("Umsätze ansehen");
        btnCreditHistory.addClickListener(e -> openCreditHistoryDialog(user));

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(user));

        return new HorizontalLayout(btnEdit, btnCredit, btnCreditHistory, btnDelete);
    }

    private void openCreateDialog() {
        new UserFormDialog(this.userService, this.userGroupService, null, this::loadData).open();
    }

    private void openEditDialog(UserEntity user) {
        new UserFormDialog(this.userService, this.userGroupService, user, this::loadData).open();
    }

    private void openCreditTopUpDialog(UserEntity user) {
        new CreditTopUpDialog(this.creditService, user, this.actingAdminName, this::loadData).open();
    }

    private void openCreditHistoryDialog(UserEntity user) {
        new CreditHistoryDialog(this.creditService, user).open();
    }

    private void confirmDelete(UserEntity user) {
        ConfirmDeleteDialog.show("Benutzer löschen",
                "Möchten Sie diesen Benutzer wirklich löschen? " + user.getName(), () -> {
                    this.userService.delete(user);
                    loadData();
                });
    }

    private void loadData() {
        this.grid.setItems(this.userService.findAllActive());
    }
}
