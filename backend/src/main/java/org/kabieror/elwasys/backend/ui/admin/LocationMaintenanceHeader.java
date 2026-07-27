package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.List;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.ui.admin.dialog.LogViewerDialog;
import org.kabieror.elwasys.backend.ui.component.Notifications;
import org.kabieror.elwasys.backend.ws.TerminalMaintenanceService;
import org.kabieror.elwasys.backend.ws.TerminalNotConnectedException;
import org.kabieror.elwasys.backend.ws.TerminalRequestTimeoutException;

/**
 * Die Kopfzeile eines Standort-Panels im Admin-Dashboard: Name des Standorts, Verbindungsstatus
 * ("Verbunden"/"Nicht verbunden", per Tooltip "Verbunden seit ...") und die Fernwartungs-Knöpfe
 * "Log anzeigen"/"Neustart" - fachlicher Nachfolger von
 * {@code AdminDashboardLocationPanel#buildToolbar}/{@code #buildStatusInfo} (Alt-Portal),
 * vermittelt über {@link TerminalMaintenanceService} statt des Alt-TCP-Protokolls (siehe
 * docs/kb/05-migration-plan.md, "Entscheidungen").
 *
 * <p>Aus {@code AdminDashboardView} herausgelöst (finale Review R3c, Issue #92): die View
 * vereinte Geräte-/Standort-Rendering, Live-Update-Verdrahtung UND diese Fernwartung in einer
 * Klasse. Die Fernwartung ist der einzige Teil davon, der überhaupt nichts mit dem
 * Dashboard-Zustand zu tun hat - sie fragt beim Bauen einmal den Verbindungsstatus ab und
 * spricht danach nur noch auf Knopfdruck mit dem Terminal. Aufbau, Beschriftungen und
 * Meldungstexte sind unverändert.
 */
public class LocationMaintenanceHeader extends HorizontalLayout {

    private final TerminalMaintenanceService maintenanceService;
    private final LocationEntity location;

    public LocationMaintenanceHeader(LocationEntity location, TerminalMaintenanceService maintenanceService) {
        this.location = location;
        this.maintenanceService = maintenanceService;

        addClassName("dashboard-location-header");
        setWidthFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        H3 title = new H3(location.getName());

        boolean connected = maintenanceService.isConnected(location.getId());
        Span connectionBadge = new Span(connected ? "Verbunden" : "Nicht verbunden");
        connectionBadge.getElement().getThemeList().add("badge" + (connected ? " success" : " error"));
        if (connected) {
            maintenanceService.connectedSince(location.getId())
                    .ifPresent(since -> connectionBadge.setTitle("Verbunden seit " + since));
        }

        Button btnLog = new Button("Log anzeigen", new Icon(VaadinIcon.FILE_TEXT), e -> showLog());
        btnLog.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button btnRestart = new Button("Neustart", new Icon(VaadinIcon.POWER_OFF), e -> restart());
        btnRestart.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

        HorizontalLayout leftGroup = new HorizontalLayout(title, connectionBadge);
        leftGroup.setAlignItems(FlexComponent.Alignment.CENTER);

        HorizontalLayout toolbar = new HorizontalLayout(btnLog, btnRestart);

        add(leftGroup, toolbar);
    }

    /**
     * Fordert den Log-Inhalt des Terminals an ({@link TerminalMaintenanceService#requestLog})
     * und zeigt ihn in {@link LogViewerDialog} - fachlicher Nachfolger des Log-Knopfs im
     * Alt-Dashboard. Für ein nicht verbundenes Terminal erscheint dieselbe Fehlermeldung wie im
     * Alt-Code ("Keine Verbindung zum Client").
     */
    private void showLog() {
        try {
            List<String> lines = this.maintenanceService.requestLog(this.location.getId());
            new LogViewerDialog(lines).open();
        } catch (TerminalNotConnectedException e) {
            Notifications.showError("Keine Verbindung zum Client");
        } catch (TerminalRequestTimeoutException e) {
            Notifications.showError("Der Client hat nicht rechtzeitig geantwortet.");
        }
    }

    /**
     * Fordert einen Neustart des Terminals an
     * ({@link TerminalMaintenanceService#requestRestart}) - fachlicher Nachfolger des
     * "Anwendung neu starten"-Menüpunkts im Alt-Dashboard.
     */
    private void restart() {
        try {
            this.maintenanceService.requestRestart(this.location.getId());
            Notifications.showSuccess("Der Neustart wurde in Auftrag gegeben.");
        } catch (TerminalNotConnectedException e) {
            Notifications.showError("Keine Verbindung zum Standort.");
        } catch (TerminalRequestTimeoutException e) {
            Notifications.showError("Der Client hat den Neustart nicht rechtzeitig bestätigt.");
        }
    }
}
