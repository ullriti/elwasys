package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.events.DeviceChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.DeviceFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Geräteverwaltung (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../views/DevicesView} (Alt-Portal, Testfälle P10/P11).
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * ein Gerät anlegt, bearbeitet oder löscht.
 */
@Route(value = "admin/devices", layout = AdminLayout.class)
@PageTitle("Geräte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDevicesView extends VerticalLayout {

    private final DeviceService deviceService;
    private final LocationService locationService;
    private final ProgramService programService;
    private final UserGroupService userGroupService;
    private final UiBroadcaster broadcaster;

    private final Grid<DeviceEntity> grid = new Grid<>();

    private Registration broadcasterRegistration;

    public AdminDevicesView(DeviceService deviceService, LocationService locationService,
            ProgramService programService, UserGroupService userGroupService, UiBroadcaster broadcaster) {
        this.deviceService = deviceService;
        this.locationService = locationService;
        this.programService = programService;
        this.userGroupService = userGroupService;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName("admin-devices-view");

        H2 title = new H2("Geräte");
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

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.broadcasterRegistration = this.broadcaster.register(attachEvent.getUI(), event -> {
            if (event instanceof DeviceChangedEvent) {
                loadData();
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

    private void configureGrid() {
        this.grid.setSizeFull();
        this.grid.addColumn(DeviceEntity::getPosition).setHeader("Position").setSortable(true).setFlexGrow(0)
                .setWidth("90px");
        this.grid.addColumn(DeviceEntity::getName).setHeader("Name").setSortable(true);
        this.grid.addColumn(d -> d.getLocation().getName()).setHeader("Standort").setSortable(true);
        this.grid.addComponentColumn(this::statusBadge).setHeader("Status");
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("110px");
    }

    private Span statusBadge(DeviceEntity device) {
        Span badge = new Span(device.isEnabled() ? "Aktiviert" : "Deaktiviert");
        badge.getElement().getThemeList().add("badge" + (device.isEnabled() ? " success" : "contrast"));
        return badge;
    }

    private HorizontalLayout actionButtons(DeviceEntity device) {
        Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
        btnEdit.setTooltipText("Bearbeiten");
        btnEdit.addClickListener(e -> openEditDialog(device));

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(device));

        return new HorizontalLayout(btnEdit, btnDelete);
    }

    private void openCreateDialog() {
        new DeviceFormDialog(this.deviceService, this.locationService, this.programService, this.userGroupService,
                null, this::loadData).open();
    }

    private void openEditDialog(DeviceEntity device) {
        new DeviceFormDialog(this.deviceService, this.locationService, this.programService, this.userGroupService,
                device, this::loadData).open();
    }

    private void confirmDelete(DeviceEntity device) {
        ConfirmDeleteDialog.show("Gerät löschen",
                "Möchten Sie dieses Gerät wirklich löschen? " + device.getName(), () -> {
                    try {
                        this.deviceService.delete(device);
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
        this.grid.setItems(this.deviceService.findAll());
    }
}
