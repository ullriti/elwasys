package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
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
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.LocationFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Standortverwaltung (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../components/LocationWindow} (Alt-Portal, Testfall P14), jetzt als
 * eigener Menüpunkt statt eines Dashboard-Dialogs (siehe {@code AdminLayout}-Javadoc: vom
 * Auftraggeber gewünschte UX-Verbesserung, keine Funktionsänderung). Ergänzt um Anlegen/
 * Löschen, die es im Alt-Fenster mangels eigener Ansicht so nicht gab.
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * einen Standort anlegt, bearbeitet oder löscht.
 */
@Route(value = "admin/locations", layout = AdminLayout.class)
@PageTitle("Standorte - Waschportal")
@RolesAllowed("ADMIN")
public class AdminLocationsView extends VerticalLayout {

    private final LocationService locationService;
    private final UserGroupService userGroupService;
    private final UiBroadcaster broadcaster;

    private final Grid<LocationEntity> grid = new Grid<>();

    private Registration broadcasterRegistration;

    public AdminLocationsView(LocationService locationService, UserGroupService userGroupService,
            UiBroadcaster broadcaster) {
        this.locationService = locationService;
        this.userGroupService = userGroupService;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName("admin-locations-view");

        H2 title = new H2("Standorte");
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
            if (event instanceof LocationChangedEvent) {
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
        this.grid.addColumn(LocationEntity::getName).setHeader("Name").setSortable(true);
        this.grid.addColumn(l -> l.getValidUserGroups().size()).setHeader("Benutzergruppen");
        this.grid.addColumn(l -> l.getOfflineMaxDurationMinutes() + " min").setHeader("Offline-Maximaldauer");
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("110px");
    }

    private HorizontalLayout actionButtons(LocationEntity location) {
        Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
        btnEdit.setTooltipText("Bearbeiten");
        btnEdit.addClickListener(e -> openEditDialog(location));

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(location));

        return new HorizontalLayout(btnEdit, btnDelete);
    }

    private void openCreateDialog() {
        new LocationFormDialog(this.locationService, this.userGroupService, null, this::loadData).open();
    }

    private void openEditDialog(LocationEntity location) {
        new LocationFormDialog(this.locationService, this.userGroupService, location, this::loadData).open();
    }

    private void confirmDelete(LocationEntity location) {
        ConfirmDeleteDialog.show("Standort löschen",
                "Möchten Sie diesen Standort wirklich löschen? " + location.getName(), () -> {
                    try {
                        this.locationService.delete(location);
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
        this.grid.setItems(this.locationService.findAll());
    }
}
