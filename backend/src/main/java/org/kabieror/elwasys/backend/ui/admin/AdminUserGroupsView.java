package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.text.NumberFormat;
import java.util.Locale;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.UserGroupFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;

/**
 * Benutzergruppenverwaltung (Phase 3 AP2, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../views/UserGroupsView} (Alt-Portal, Testfälle P9/P13).
 */
@Route(value = "admin/user-groups", layout = AdminLayout.class)
@PageTitle("Benutzergruppen - Waschportal")
@RolesAllowed("ADMIN")
public class AdminUserGroupsView extends VerticalLayout {

    private final UserGroupService userGroupService;
    private final LocationService locationService;
    private final DeviceService deviceService;
    private final ProgramService programService;

    private final Grid<UserGroupEntity> grid = new Grid<>();

    public AdminUserGroupsView(UserGroupService userGroupService, LocationService locationService,
            DeviceService deviceService, ProgramService programService) {
        this.userGroupService = userGroupService;
        this.locationService = locationService;
        this.deviceService = deviceService;
        this.programService = programService;

        setSizeFull();
        addClassName("admin-user-groups-view");

        H2 title = new H2("Benutzergruppen");
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
        this.grid.addColumn(UserGroupEntity::getName).setHeader("Name").setSortable(true);
        this.grid.addColumn(this::formatDiscount).setHeader("Rabatt");
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("110px");
    }

    private String formatDiscount(UserGroupEntity group) {
        if (group.getDiscountType() == DiscountType.FIX) {
            return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(group.getDiscountValue());
        } else if (group.getDiscountType() == DiscountType.FACTOR) {
            return NumberFormat.getPercentInstance(Locale.GERMANY).format(group.getDiscountValue());
        }
        return "-";
    }

    private HorizontalLayout actionButtons(UserGroupEntity group) {
        Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
        btnEdit.setTooltipText("Bearbeiten");
        btnEdit.addClickListener(e -> openEditDialog(group));

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(group));

        return new HorizontalLayout(btnEdit, btnDelete);
    }

    private void openCreateDialog() {
        new UserGroupFormDialog(this.userGroupService, this.locationService, this.deviceService, this.programService,
                null, this::loadData).open();
    }

    private void openEditDialog(UserGroupEntity group) {
        new UserGroupFormDialog(this.userGroupService, this.locationService, this.deviceService, this.programService,
                group, this::loadData).open();
    }

    private void confirmDelete(UserGroupEntity group) {
        ConfirmDeleteDialog.show("Benutzergruppe löschen",
                "Möchten Sie diese Benutzergruppe wirklich löschen? " + group.getName()
                        + " Benutzern, denen die Gruppe derzeit zugewiesen ist, wird eine andere Gruppe zugewiesen.",
                () -> {
                    try {
                        this.userGroupService.delete(group);
                    } catch (EntityInUseException e) {
                        Notification notification = Notification.show(e.getMessage(), 5000,
                                Notification.Position.MIDDLE);
                        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
                        return;
                    }
                    loadData();
                });
    }

    private void loadData() {
        this.grid.setItems(this.userGroupService.findAll());
    }
}
