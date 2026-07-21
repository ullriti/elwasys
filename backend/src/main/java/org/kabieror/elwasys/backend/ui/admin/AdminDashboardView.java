package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.events.DeviceChangedEvent;
import org.kabieror.elwasys.backend.events.DomainEvent;
import org.kabieror.elwasys.backend.events.ExecutionChangedEvent;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;
import org.kabieror.elwasys.backend.service.DashboardService;
import org.kabieror.elwasys.backend.service.DashboardService.DeviceStatus;
import org.kabieror.elwasys.backend.service.DashboardService.LocationStatus;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.ui.admin.dialog.LogViewerDialog;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;
import org.kabieror.elwasys.backend.ws.TerminalMaintenanceService;
import org.kabieror.elwasys.backend.ws.TerminalNotConnectedException;
import org.kabieror.elwasys.backend.ws.TerminalRequestTimeoutException;

/**
 * Admin-Dashboard (Phase 3 AP3, siehe kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../views/AdminDashboardView} (Alt-Portal): zeigt je Standort dessen Geräte
 * mit "Frei"/"Besetzt" (aus der laufenden Execution abgeleitet, Testfall P20), bei einer
 * laufenden Ausführung zusätzlich Programm, Benutzer und Restzeit, sowie je Gerät die
 * vollständige Ausführungshistorie (Datum/Benutzer/Dauer/Preis), analog zur Tabelle im
 * Alt-{@code AdminDashboardLocationPanel} inkl. Hervorhebung laufender/abgelaufener Zeilen.
 *
 * <p>Die eigentliche Datenbeschaffung liegt in {@link DashboardService} (siehe dessen
 * Klassenkommentar) - diese View aktualisiert beim Seitenaufruf ({@link #loadData()}).
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
 * die View meldet sich in {@link #onAttach} beim {@link UiBroadcaster} an und in {@link
 * #onDetach} wieder ab. Bei einem {@link DeviceChangedEvent}/{@link ExecutionChangedEvent}
 * eines Geräts, das gerade auf dieser Seite angezeigt wird, lädt {@link #refreshDevice} GEZIELT
 * nur das eine betroffene Geräte-Panel neu (über {@link DashboardService#getDeviceStatus}, das
 * genau dafür entworfen wurde, siehe dessen Javadoc) statt die gesamte Seite neu aufzubauen; ein
 * {@link LocationChangedEvent} oder ein Ereignis für ein (noch) nicht angezeigtes Gerät (z.B.
 * gerade neu angelegt) löst einen vollständigen {@link #loadData()} aus.
 *
 * <p><b>Seit Phase 3 AP4</b>: je Standort eine Fernwartungs-Toolbar (Log anzeigen/Neustart)
 * plus Verbindungsstatus - fachlicher Nachfolger der Wartungsverbindungs-Toolbar des
 * Alt-Dashboards ({@code AdminDashboardLocationPanel#buildToolbar}/{@code #buildStatusInfo}),
 * vermittelt über {@link TerminalMaintenanceService} statt des Alt-TCP-Protokolls (siehe
 * kb/05-migration-plan.md, "Entscheidungen": das Alt-TCP-Protokoll wird NICHT portiert, das
 * Alt-Portal bleibt dafür bis zum Cutover in Betrieb). Statt der Alt-"IP-Adresse" (obsolet,
 * siehe kb/02-data-model.md: {@code client_ip}/{@code -port} entfallen mit der ausgehenden
 * Verbindung) zeigt diese View "Verbunden seit". Da sich Alt-Terminals laut Roadmap ERST in
 * Phase 4 über diesen Kanal verbinden, zeigt diese Toolbar in der Praxis i.d.R. "Nicht
 * verbunden" - genau der laut Auftrag geforderte klare Zustand.
 */
@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Dashboard - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDashboardView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.SHORT).withLocale(Locale.GERMANY);

    private final DashboardService dashboardService;
    private final DeviceService deviceService;
    private final ExecutionService executionService;
    private final TerminalMaintenanceService maintenanceService;
    private final UiBroadcaster broadcaster;

    private final VerticalLayout locationsContainer = new VerticalLayout();
    private final Map<Integer, VerticalLayout> devicePanelsByDeviceId = new HashMap<>();

    private Registration broadcasterRegistration;

    public AdminDashboardView(DashboardService dashboardService, DeviceService deviceService,
            ExecutionService executionService, TerminalMaintenanceService maintenanceService,
            UiBroadcaster broadcaster) {
        this.dashboardService = dashboardService;
        this.deviceService = deviceService;
        this.executionService = executionService;
        this.maintenanceService = maintenanceService;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName("admin-dashboard-view");

        add(new H2("Dashboard"));

        this.locationsContainer.setPadding(false);
        add(this.locationsContainer);
        setFlexGrow(1, this.locationsContainer);

        loadData();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        this.broadcasterRegistration = this.broadcaster.register(attachEvent.getUI(), this::onDomainEvent);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (this.broadcasterRegistration != null) {
            this.broadcasterRegistration.remove();
            this.broadcasterRegistration = null;
        }
        super.onDetach(detachEvent);
    }

    private void onDomainEvent(DomainEvent event) {
        switch (event) {
            case DeviceChangedEvent(Integer deviceId) -> refreshDevice(deviceId);
            case ExecutionChangedEvent(Integer executionId, Integer deviceId, Integer userId) -> refreshDevice(
                    deviceId);
            case LocationChangedEvent ignored -> loadData();
            default -> {
                // andere Ereignisarten (Benutzer/Gruppen/Programme/Guthaben) betreffen dieses
                // Dashboard nicht.
            }
        }
    }

    /**
     * Lädt gezielt das Panel des betroffenen Geräts neu (siehe Klassen-Javadoc). Ist das Gerät
     * (noch) nicht Teil dieser Seite - z.B. gerade erst angelegt, oder die Seite wurde vor dem
     * betroffenen Standort noch nie geladen - fällt die Methode auf einen vollständigen
     * {@link #loadData()} zurück.
     */
    private void refreshDevice(Integer deviceId) {
        if (deviceId == null) {
            loadData();
            return;
        }
        VerticalLayout panel = this.devicePanelsByDeviceId.get(deviceId);
        if (panel == null) {
            loadData();
            return;
        }
        this.deviceService.findById(deviceId).ifPresentOrElse(
                device -> populateDevicePanel(panel, this.dashboardService.getDeviceStatus(device)), this::loadData);
    }

    private void loadData() {
        this.locationsContainer.removeAll();
        this.devicePanelsByDeviceId.clear();
        for (LocationStatus locationStatus : this.dashboardService.getLocationStatuses()) {
            this.locationsContainer.add(buildLocationPanel(locationStatus));
        }
    }

    private VerticalLayout buildLocationPanel(LocationStatus locationStatus) {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("dashboard-location-panel");
        panel.setPadding(false);
        panel.add(buildLocationHeader(locationStatus.location()));

        FlexLayout devices = new FlexLayout();
        devices.addClassName("dashboard-device-list");
        devices.getStyle().set("flex-wrap", "wrap").set("gap", "1rem");
        for (DeviceStatus deviceStatus : locationStatus.devices()) {
            devices.add(buildDevicePanel(deviceStatus));
        }
        panel.add(devices);
        return panel;
    }

    /**
     * Kopfzeile eines Standort-Panels: Name, Verbindungsstatus ("Verbunden seit"/"Nicht
     * verbunden") und die Fernwartungs-Knöpfe - fachlicher Nachfolger von
     * {@code AdminDashboardLocationPanel#buildToolbar}/{@code #buildStatusInfo}.
     */
    private HorizontalLayout buildLocationHeader(LocationEntity location) {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("dashboard-location-header");
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H3 title = new H3(location.getName());

        boolean connected = this.maintenanceService.isConnected(location.getId());
        Span connectionBadge = new Span(connected ? "Verbunden" : "Nicht verbunden");
        connectionBadge.getElement().getThemeList().add("badge" + (connected ? " success" : " error"));
        if (connected) {
            this.maintenanceService.connectedSince(location.getId())
                    .ifPresent(since -> connectionBadge.setTitle("Verbunden seit " + since));
        }

        Button btnLog = new Button("Log anzeigen", new Icon(VaadinIcon.FILE_TEXT),
                e -> showLog(location));
        btnLog.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button btnRestart = new Button("Neustart", new Icon(VaadinIcon.POWER_OFF), e -> restart(location));
        btnRestart.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

        HorizontalLayout leftGroup = new HorizontalLayout(title, connectionBadge);
        leftGroup.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout toolbar = new HorizontalLayout(btnLog, btnRestart);

        header.add(leftGroup, toolbar);
        return header;
    }

    /**
     * Fordert den Log-Inhalt des Terminals an ({@link TerminalMaintenanceService#requestLog})
     * und zeigt ihn in {@link LogViewerDialog} - fachlicher Nachfolger des Log-Knopfs im
     * Alt-Dashboard. Für ein nicht verbundenes Terminal (aktuell der Regelfall, siehe
     * Klassen-Javadoc) erscheint dieselbe Fehlermeldung wie im Alt-Code ("Keine Verbindung
     * zum Client").
     */
    private void showLog(LocationEntity location) {
        try {
            List<String> lines = this.maintenanceService.requestLog(location.getId());
            new LogViewerDialog(lines).open();
        } catch (TerminalNotConnectedException e) {
            showError("Keine Verbindung zum Client");
        } catch (TerminalRequestTimeoutException e) {
            showError("Der Client hat nicht rechtzeitig geantwortet.");
        }
    }

    /**
     * Fordert einen Neustart des Terminals an
     * ({@link TerminalMaintenanceService#requestRestart}) - fachlicher Nachfolger des
     * "Anwendung neu starten"-Menüpunkts im Alt-Dashboard.
     */
    private void restart(LocationEntity location) {
        try {
            this.maintenanceService.requestRestart(location.getId());
            showSuccess("Der Neustart wurde in Auftrag gegeben.");
        } catch (TerminalNotConnectedException e) {
            showError("Keine Verbindung zum Standort.");
        } catch (TerminalRequestTimeoutException e) {
            showError("Der Client hat den Neustart nicht rechtzeitig bestätigt.");
        }
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private VerticalLayout buildDevicePanel(DeviceStatus deviceStatus) {
        VerticalLayout devicePanel = new VerticalLayout();
        devicePanel.addClassName("dashboard-device-panel");
        devicePanel.getElement().getThemeList().add("spacing-s");
        devicePanel.setWidth("24em");
        populateDevicePanel(devicePanel, deviceStatus);
        this.devicePanelsByDeviceId.put(deviceStatus.device().getId(), devicePanel);
        return devicePanel;
    }

    /**
     * Baut den Inhalt eines Geräte-Panels neu auf, OHNE ein neues Panel (und damit einen neuen
     * DOM-Knoten) zu erzeugen - genutzt sowohl beim erstmaligen Aufbau ({@link
     * #buildDevicePanel}) als auch beim gezielten Live-Update eines einzelnen Geräts ({@link
     * #refreshDevice}, Phase 3 AP5).
     */
    private void populateDevicePanel(VerticalLayout devicePanel, DeviceStatus deviceStatus) {
        devicePanel.removeAll();

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        Span nameLabel = new Span(deviceStatus.device().getName());
        nameLabel.addClassName("device-name");
        Span statusBadge = new Span(deviceStatus.isOccupied() ? "Besetzt" : "Frei");
        statusBadge.getElement().getThemeList().add("badge" + (deviceStatus.isOccupied() ? " error" : " success"));
        header.add(nameLabel, statusBadge);
        devicePanel.add(header);

        deviceStatus.runningExecution().ifPresent(execution -> devicePanel.add(buildRunningInfo(execution,
                deviceStatus.remainingTime())));

        devicePanel.add(buildHistoryGrid(deviceStatus));
    }

    private Span buildRunningInfo(ExecutionEntity execution, Duration remainingTime) {
        String user = execution.getUser() == null ? "-" : execution.getUser().getName();
        String program = execution.getProgram() == null ? "-" : execution.getProgram().getName();
        Span info = new Span(
                "Programm: " + program + " · Nutzer: " + user + " · Restzeit: " + formatDuration(remainingTime));
        info.addClassName("dashboard-device-running-info");
        return info;
    }

    private Grid<ExecutionEntity> buildHistoryGrid(DeviceStatus deviceStatus) {
        Grid<ExecutionEntity> grid = new Grid<>();
        grid.addClassName("dashboard-device-history");
        grid.setHeight("14em");
        grid.setWidthFull();

        grid.addColumn(e -> e.getStart() == null ? "-" : e.getStart().format(DATE_TIME_FORMAT)).setHeader("Datum");
        grid.addColumn(e -> e.getUser() == null ? "-" : e.getUser().getName()).setHeader("Benutzer");
        grid.addColumn(e -> formatDuration(elapsedOf(e))).setHeader("Dauer");
        grid.addColumn(e -> formatCurrency(this.executionService.getPrice(e))).setHeader("Preis");

        grid.setPartNameGenerator(e -> {
            if (!e.isFinished() && e.getStart() != null) {
                return this.executionService.isExpired(e) ? "expired-execution" : "running-execution";
            }
            return null;
        });

        grid.setItems(deviceStatus.executions());
        return grid;
    }

    private static Duration elapsedOf(ExecutionEntity execution) {
        if (execution.getStart() == null) {
            return Duration.ZERO;
        }
        java.time.LocalDateTime end = execution.getStop() != null ? execution.getStop()
                : java.time.LocalDateTime.now();
        return Duration.between(execution.getStart(), end);
    }

    private static String formatDuration(Duration duration) {
        if (duration == null) {
            return "-";
        }
        long totalSeconds = Math.max(0, duration.getSeconds());
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatCurrency(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount);
    }
}
