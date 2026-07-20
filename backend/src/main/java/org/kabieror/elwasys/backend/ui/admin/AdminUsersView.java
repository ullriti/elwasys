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
import jakarta.annotation.security.RolesAllowed;
import java.text.NumberFormat;
import java.util.Locale;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.ui.admin.dialog.UserFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;

/**
 * Benutzerverwaltung (Phase 3 AP2, siehe kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../views/UsersView} (Alt-Portal, Testfälle P6-P8).
 *
 * <p>Guthaben wird hier NUR angezeigt ({@link CreditService#getCredit(UserEntity)}, 1:1 wie
 * die Alt-Tabellenspalte "Guthaben") - Aufladen (Alt: {@code UserCreditWindow}) ist
 * ausdrücklich AP3, nicht Teil dieses Arbeitspakets. Ebenfalls nicht Teil: der
 * Admin-Passwort-Reset (AP4) und die Warnung bei nicht abgerechneten Programmausführungen
 * ({@code ExpiredExecutionsWindow}, Alt-Icon-Spalte) - beide sind eigenständige
 * Dialoge/Funktionen laut Roadmap, nicht Teil des Stammdaten-Kerns dieses Arbeitspakets.
 */
@Route(value = "admin/users", layout = AdminLayout.class)
@PageTitle("Benutzer - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUsersView extends VerticalLayout {

    private final UserService userService;
    private final UserGroupService userGroupService;
    private final CreditService creditService;

    private final Grid<UserEntity> grid = new Grid<>();

    public AdminUsersView(UserService userService, UserGroupService userGroupService, CreditService creditService) {
        this.userService = userService;
        this.userGroupService = userGroupService;
        this.creditService = creditService;

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
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("110px");
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

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(user));

        return new HorizontalLayout(btnEdit, btnDelete);
    }

    private void openCreateDialog() {
        new UserFormDialog(this.userService, this.userGroupService, null, this::loadData).open();
    }

    private void openEditDialog(UserEntity user) {
        new UserFormDialog(this.userService, this.userGroupService, user, this::loadData).open();
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
