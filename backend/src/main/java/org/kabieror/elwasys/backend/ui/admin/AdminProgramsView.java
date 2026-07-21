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
import java.text.NumberFormat;
import java.util.Locale;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.events.ProgramChangedEvent;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.ui.admin.dialog.ProgramFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;

/**
 * Programmverwaltung (Phase 3 AP2, siehe kb/05-migration-plan.md) - fachlicher Nachfolger
 * von {@code Portal/.../views/ProgramsView} (Alt-Portal, Testfall P12) inkl. des
 * Lösch-Wächters ("Programm ist noch auf N Gerät(en) verfügbar").
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die Liste lädt sich über den {@link UiBroadcaster} automatisch neu, wenn irgendeine Session
 * ein Programm anlegt, bearbeitet oder löscht.
 */
@Route(value = "admin/programs", layout = AdminLayout.class)
@PageTitle("Programme - Waschportal")
@RolesAllowed("ADMIN")
public class AdminProgramsView extends VerticalLayout {

    private final ProgramService programService;
    private final UserGroupService userGroupService;
    private final UiBroadcaster broadcaster;

    private final Grid<ProgramEntity> grid = new Grid<>();

    private Registration broadcasterRegistration;

    public AdminProgramsView(ProgramService programService, UserGroupService userGroupService,
            UiBroadcaster broadcaster) {
        this.programService = programService;
        this.userGroupService = userGroupService;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName("admin-programs-view");

        H2 title = new H2("Programme");
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
            if (event instanceof ProgramChangedEvent) {
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
        this.grid.addColumn(ProgramEntity::getName).setHeader("Name").setSortable(true);
        this.grid.addColumn(p -> p.getType() == ProgramType.DYNAMIC ? "Dynamisch" : "Statisch").setHeader("Typ")
                .setSortable(true);
        this.grid.addColumn(this::formatPrice).setHeader("Preis");
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("110px");
    }

    private String formatPrice(ProgramEntity program) {
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        if (program.getType() == ProgramType.DYNAMIC) {
            String unit = switch (program.getTimeUnit()) {
                case HOURS -> "h";
                case MINUTES -> "min";
                case SECONDS -> "s";
                case null -> "?";
            };
            return currency.format(program.getFlagfall()) + " + " + currency.format(program.getRate()) + " / "
                    + unit;
        }
        return currency.format(program.getFlagfall());
    }

    private HorizontalLayout actionButtons(ProgramEntity program) {
        Button btnEdit = new Button(new Icon(VaadinIcon.EDIT));
        btnEdit.setTooltipText("Bearbeiten");
        btnEdit.addClickListener(e -> openEditDialog(program));

        Button btnDelete = new Button(new Icon(VaadinIcon.TRASH));
        btnDelete.setTooltipText("Löschen");
        btnDelete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        btnDelete.addClickListener(e -> confirmDelete(program));

        return new HorizontalLayout(btnEdit, btnDelete);
    }

    private void openCreateDialog() {
        new ProgramFormDialog(this.programService, this.userGroupService, null, this::loadData).open();
    }

    private void openEditDialog(ProgramEntity program) {
        new ProgramFormDialog(this.programService, this.userGroupService, program, this::loadData).open();
    }

    private void confirmDelete(ProgramEntity program) {
        ConfirmDeleteDialog.show("Programm löschen",
                "Möchten Sie dieses Programm wirklich löschen? " + program.getName(), () -> {
                    try {
                        this.programService.delete(program);
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
        this.grid.setItems(this.programService.findAll());
    }
}
