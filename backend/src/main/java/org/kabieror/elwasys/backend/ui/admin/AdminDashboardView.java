package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.service.DashboardService;
import org.kabieror.elwasys.backend.service.DashboardService.DeviceStatus;
import org.kabieror.elwasys.backend.service.DashboardService.LocationStatus;
import org.kabieror.elwasys.backend.service.ExecutionService;

/**
 * Admin-Dashboard (Phase 3 AP3, siehe kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../views/AdminDashboardView} (Alt-Portal): zeigt je Standort dessen Geräte
 * mit "Frei"/"Besetzt" (aus der laufenden Execution abgeleitet, Testfall P20), bei einer
 * laufenden Ausführung zusätzlich Programm, Benutzer und Restzeit, sowie je Gerät die
 * vollständige Ausführungshistorie (Datum/Benutzer/Dauer/Preis), analog zur Tabelle im
 * Alt-{@code AdminDashboardLocationPanel} inkl. Hervorhebung laufender/abgelaufener Zeilen.
 *
 * <p>Die eigentliche Datenbeschaffung liegt in {@link DashboardService} (siehe dessen
 * Klassenkommentar) - diese View aktualisiert beim Seitenaufruf ({@link #loadData()}), ein
 * Live-Push zwischen Sessions ist laut Auftrag dieses Arbeitspakets nicht nötig und folgt in
 * AP5.
 *
 * <p>NICHT Teil dieser View (siehe kb/05-migration-plan.md Phase-3-Roadmap): die
 * Wartungsverbindungs-Toolbar des Alt-Dashboards (Log-Datei ansehen, Client neu starten,
 * Verbindungsstatus/IP) - das ist Teil der für AP4 geplanten Fernwartung über den neuen
 * Backend-Kanal.
 */
@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Dashboard - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDashboardView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.SHORT).withLocale(Locale.GERMANY);

    private final DashboardService dashboardService;
    private final ExecutionService executionService;

    private final VerticalLayout locationsContainer = new VerticalLayout();

    public AdminDashboardView(DashboardService dashboardService, ExecutionService executionService) {
        this.dashboardService = dashboardService;
        this.executionService = executionService;

        setSizeFull();
        addClassName("admin-dashboard-view");

        add(new H2("Dashboard"));

        this.locationsContainer.setPadding(false);
        add(this.locationsContainer);
        setFlexGrow(1, this.locationsContainer);

        loadData();
    }

    private void loadData() {
        this.locationsContainer.removeAll();
        for (LocationStatus locationStatus : this.dashboardService.getLocationStatuses()) {
            this.locationsContainer.add(buildLocationPanel(locationStatus));
        }
    }

    private VerticalLayout buildLocationPanel(LocationStatus locationStatus) {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("dashboard-location-panel");
        panel.setPadding(false);
        panel.add(new H3(locationStatus.location().getName()));

        FlexLayout devices = new FlexLayout();
        devices.addClassName("dashboard-device-list");
        devices.getStyle().set("flex-wrap", "wrap").set("gap", "1rem");
        for (DeviceStatus deviceStatus : locationStatus.devices()) {
            devices.add(buildDevicePanel(deviceStatus));
        }
        panel.add(devices);
        return panel;
    }

    private VerticalLayout buildDevicePanel(DeviceStatus deviceStatus) {
        VerticalLayout devicePanel = new VerticalLayout();
        devicePanel.addClassName("dashboard-device-panel");
        devicePanel.getElement().getThemeList().add("spacing-s");
        devicePanel.setWidth("24em");

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
        return devicePanel;
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
