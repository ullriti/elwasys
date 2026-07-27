package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.security.RolesAllowed;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.events.DeviceChangedEvent;
import org.kabieror.elwasys.backend.events.DomainEvent;
import org.kabieror.elwasys.backend.events.ExecutionChangedEvent;
import org.kabieror.elwasys.backend.events.LocationChangedEvent;
import org.kabieror.elwasys.backend.service.DashboardService;
import org.kabieror.elwasys.backend.service.DashboardService.DeviceStatus;
import org.kabieror.elwasys.backend.service.DashboardService.LocationStatus;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;
import org.kabieror.elwasys.backend.ws.TerminalMaintenanceService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Admin-Dashboard (Phase 3 AP3, siehe docs/kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../views/AdminDashboardView} (Alt-Portal): zeigt je Standort dessen Geräte
 * mit "Frei"/"Besetzt" (aus der laufenden Execution abgeleitet, Testfall P20), bei einer
 * laufenden Ausführung zusätzlich Programm, Benutzer und Restzeit, sowie je Gerät die
 * vollständige Ausführungshistorie (Datum/Benutzer/Dauer/Preis), analog zur Tabelle im
 * Alt-{@code AdminDashboardLocationPanel} inkl. Hervorhebung laufender/abgelaufener Zeilen.
 *
 * <p>Die eigentliche Datenbeschaffung liegt in {@link DashboardService} (siehe dessen
 * Klassenkommentar) - diese View aktualisiert beim Seitenaufruf ({@link #loadData()}).
 *
 * <p><b>Seit Phase 3 AP5</b> (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions"):
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
 * docs/kb/05-migration-plan.md, "Entscheidungen": das Alt-TCP-Protokoll wurde NICHT portiert).
 * Statt der Alt-"IP-Adresse" (obsolet, siehe docs/kb/02-data-model.md: {@code client_ip}/
 * {@code -port} entfallen mit der ausgehenden Verbindung) zeigt sie "Verbunden seit". Seit
 * Phase 4 verbinden sich die Terminals über diesen Kanal; "Nicht verbunden" ist damit der
 * Ausnahme- und nicht mehr der Regelfall (Kommentar auf den Ist-Zustand nachgezogen, #93). Sie
 * liegt seit Issue #92 in einer eigenen Komponente ({@link LocationMaintenanceHeader}).
 *
 * <p><b>Seit Issue #89</b>: über den Standort-Panels ein Hinweisstreifen auf offene
 * Offline-Vorfälle mit Link in {@link AdminOfflineIncidentsView} (siehe
 * {@link #refreshIncidentBanner()}) - ohne offene Vorfälle bleibt er unsichtbar.
 *
 * <p><b>Seit dem UI-Redesign v2</b> (docs/specs/0002-ui-design/v2/MAPPING.md, "Dashboard-
 * Gerätekarten", siehe docs/kb/05-migration-plan.md): die Gerätekarte zeigt dieselben Daten in
 * neuer Anordnung - Statuspunkt und "Deaktiviert"-Chip in der Kopfzeile, die laufende Ausführung
 * als Kennzahlen-Panel mit Fortschrittsbalken der Restzeit und die Historie kompakt unter einer
 * "Verlauf"-Überschrift mit Eintragszahl, deren Spalten sortierbar sind (die Sortierung geht in
 * die Datenbankabfrage, siehe {@link #toHistorySort}).
 */
@Route(value = "admin", layout = AdminLayout.class)
@PageTitle("Dashboard - Waschportal")
@RolesAllowed("ADMIN")
public class AdminDashboardView extends VerticalLayout {

    private final DashboardService dashboardService;
    private final DeviceService deviceService;
    private final ExecutionService executionService;
    private final TerminalMaintenanceService maintenanceService;
    private final TerminalOfflineIncidentService incidentService;
    private final UiBroadcaster broadcaster;

    private final VerticalLayout locationsContainer = new VerticalLayout();
    private final Map<Integer, VerticalLayout> devicePanelsByDeviceId = new HashMap<>();

    /** Hinweis auf offene Offline-Vorfälle (Issue #89) - nur sichtbar, wenn es welche gibt. */
    private final Div incidentBanner = new Div();

    private Registration broadcasterRegistration;

    public AdminDashboardView(DashboardService dashboardService, DeviceService deviceService,
            ExecutionService executionService, TerminalMaintenanceService maintenanceService,
            TerminalOfflineIncidentService incidentService, UiBroadcaster broadcaster) {
        this.dashboardService = dashboardService;
        this.deviceService = deviceService;
        this.executionService = executionService;
        this.maintenanceService = maintenanceService;
        this.incidentService = incidentService;
        this.broadcaster = broadcaster;

        setSizeFull();
        addClassName("admin-dashboard-view");

        this.incidentBanner.addClassName("dashboard-incident-banner");
        this.incidentBanner.setVisible(false);

        add(new H2("Dashboard"), this.incidentBanner);

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
        refreshIncidentBanner();
        this.locationsContainer.removeAll();
        this.devicePanelsByDeviceId.clear();
        for (LocationStatus locationStatus : this.dashboardService.getLocationStatuses()) {
            this.locationsContainer.add(buildLocationPanel(locationStatus));
        }
    }

    /**
     * Issue #89: offene Offline-Vorfälle bedeuten verlorenes Geld und halten den Betriebsalarm -
     * das Dashboard ist die erste Seite nach dem Admin-Login und damit die naheliegende Stelle,
     * an der ein Administrator davon erfährt. Der Hinweis verlinkt in die Vorfallsliste
     * ({@link AdminOfflineIncidentsView}), wo quittiert wird; ohne offene Vorfälle bleibt er
     * unsichtbar, damit das gewohnte Dashboard unverändert aussieht.
     */
    private void refreshIncidentBanner() {
        long openCount = this.incidentService.countOpen();
        this.incidentBanner.removeAll();
        this.incidentBanner.setVisible(openCount > 0);
        if (openCount == 0) {
            return;
        }
        this.incidentBanner.add(new Span(openCount == 1 ? "1 offener Offline-Vorfall - bitte sichten und quittieren: "
                : openCount + " offene Offline-Vorfälle - bitte sichten und quittieren: "),
                new RouterLink("Offline-Vorfälle", AdminOfflineIncidentsView.class));
    }

    private VerticalLayout buildLocationPanel(LocationStatus locationStatus) {
        VerticalLayout panel = new VerticalLayout();
        panel.addClassName("dashboard-location-panel");
        panel.setPadding(false);
        panel.add(new LocationMaintenanceHeader(locationStatus.location(), this.maintenanceService));

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
        // Keine feste Breite mehr: die Karten sind über das Theme (portal-theme.css)
        // responsiv (zwei pro Reihe auf breiten Bildschirmen, eine pro Reihe schmal) - 1:1 wie
        // das Alt-Portal (Portal/.../dashboard.scss: .device-container 50%/100%). Eine feste
        // Breite (früher 24em) ließ die Verlaufstabelle abgeschnitten wirken.
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

        // Status-Klasse für den farbigen oberen Kartenrand (gleiche Palette wie das Terminal:
        // frei = grün, besetzt = rot, deaktiviert = grau; siehe portal-theme.css) - auch beim
        // Live-Update (refreshDevice) neu gesetzt, daher zuerst alte Klassen entfernen.
        devicePanel.removeClassNames("device-status-free", "device-status-occupied",
                "device-status-disabled");
        String statusClass = !deviceStatus.device().isEnabled() ? "device-status-disabled"
                : deviceStatus.isOccupied() ? "device-status-occupied" : "device-status-free";
        devicePanel.addClassName(statusClass);

        devicePanel.add(buildDeviceHeader(deviceStatus, statusClass));

        deviceStatus.runningExecution().ifPresent(execution -> devicePanel.add(buildRunningInfo(execution,
                deviceStatus.remainingTime())));

        // Die Anzahl der Verlaufseinträge wird EINMAL ermittelt und an beide Stellen gereicht,
        // die sie brauchen (Überschrift und Bildlaufhöhe des lazy geladenen Grids). Sie kostet
        // ein COUNT(*) auf der größten Tabelle des Systems (Issue #30), und dieser Panel-Aufbau
        // läuft bei jedem Live-Update eines Geräts erneut.
        long historyCount = this.executionService.countExecutions(deviceStatus.device());
        devicePanel.add(buildHistoryHeader(historyCount), buildHistoryGrid(deviceStatus, historyCount));
    }

    /**
     * Die Kopfzeile der Gerätekarte (UI-Redesign v2): Statuspunkt, Gerätename und rechtsbündig
     * der "Deaktiviert"-Chip sowie das Frei/Besetzt-Abzeichen.
     */
    private static HorizontalLayout buildDeviceHeader(DeviceStatus deviceStatus, String statusClass) {
        HorizontalLayout header = new HorizontalLayout();
        header.addClassName("device-header");
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        // UI-Redesign v2: derselbe Status noch einmal als farbiger Punkt direkt vor dem Namen.
        // Der farbige obere Kartenrand allein verlangt, den Blick an den Rand der Karte zu
        // führen - der Punkt bringt die Information dahin, wo ohnehin gelesen wird. Er trägt
        // dieselbe Statusklasse wie die Karte, die Farbe kommt also aus derselben Palette.
        Span statusDot = new Span();
        statusDot.addClassNames("device-status-dot", statusClass);

        Span nameLabel = new Span(deviceStatus.device().getName());
        nameLabel.addClassName("device-name");
        Span statusBadge = new Span(deviceStatus.isOccupied() ? "Besetzt" : "Frei");
        statusBadge.getElement().getThemeList().add("badge" + (deviceStatus.isOccupied() ? " error" : " success"));

        header.add(statusDot, nameLabel);
        // Der Name füllt die Lücke zwischen Punkt und Abzeichen, damit Chip und Abzeichen
        // unabhängig von der Namenslänge immer bündig rechts stehen. Ein zusätzliches
        // JustifyContentMode.BETWEEN wäre wirkungslos: der wachsende Name lässt gar keinen
        // freien Platz mehr übrig, den es zu verteilen gäbe.
        header.setFlexGrow(1, nameLabel);
        if (!deviceStatus.device().isEnabled()) {
            // Ein deaktiviertes Gerät ist weder "Frei" noch "Besetzt" nutzbar - bisher sagte das
            // nur der graue Kartenrand, jetzt zusätzlich ein Chip in Worten (UI-Redesign v2).
            Span disabledChip = new Span("Deaktiviert");
            disabledChip.getElement().getThemeList().add("badge contrast small");
            header.add(disabledChip);
        }
        header.add(statusBadge);
        return header;
    }

    /**
     * Die laufende Ausführung als Kennzahlen-Panel (UI-Redesign v2, siehe Klassen-Javadoc):
     * dieselben drei Werte wie bisher (Programm, Nutzer, Restzeit) und dieselbe Formatierung,
     * nur als beschriftete Kennzahlen nebeneinander statt als eine mit "·" getrennte Textzeile.
     * Die bisherige CSS-Klasse bleibt zusätzlich gesetzt, damit auf das Panel weiterhin unter
     * ihrem Namen gezielt werden kann.
     */
    private static Div buildRunningInfo(ExecutionEntity execution, Duration remainingTime) {
        String user = execution.getUser() == null ? "-" : execution.getUser().getName();
        String program = execution.getProgram() == null ? "-" : execution.getProgram().getName();

        Div info = new Div();
        info.addClassNames("device-metrics", "dashboard-device-running-info");
        info.add(buildMetric("Programm", program, false), buildMetric("Nutzer", user, false),
                buildMetric("Restzeit", formatDuration(remainingTime), true),
                buildRemainingProgress(execution, remainingTime));
        return info;
    }

    /**
     * Ein Label/Wert-Paar des Kennzahlen-Panels. {@code numeric} setzt tabellarische Ziffern -
     * die Restzeit zählt sekündlich herunter und würde sonst bei jedem Live-Update leicht
     * springen, weil die Ziffern unterschiedlich breit sind.
     */
    private static Div buildMetric(String label, String value, boolean numeric) {
        Div metric = new Div();
        metric.addClassName("device-metric");
        Span labelSpan = new Span(label);
        labelSpan.addClassName("device-metric-label");
        Span valueSpan = new Span(value);
        valueSpan.addClassName("device-metric-value");
        if (numeric) {
            valueSpan.addClassName("tabular-nums");
        }
        metric.add(labelSpan, valueSpan);
        return metric;
    }

    /**
     * Fortschrittsbalken der laufenden Ausführung (UI-Redesign v2, ausdrücklicher Wunsch des
     * Auftraggebers): verstrichene Zeit im Verhältnis zur Höchstdauer des Programms - die
     * Restzeit daneben als Zahl, hier als Fläche. Er wird nicht eigenständig fortgeschrieben,
     * sondern lebt vom bestehenden Live-Update: {@link #refreshDevice} baut das gesamte
     * Geräte-Panel neu auf, der Balken bekommt seinen Wert dabei genauso frisch wie die Restzeit.
     *
     * <p>Das Programm wird ohne Null-Prüfung ausgelesen: eine laufende Ausführung hat immer
     * eines - {@link DashboardService#getDeviceStatus} greift für die Restzeit, die hier
     * daneben steht, bereits ungeschützt darauf zu. Ein defensiver Zweig wäre unerreichbar.
     *
     * <p>Zugänglichkeit: die Web-Komponente meldet sich als {@code role="progressbar"} mit einem
     * {@code aria-valuenow} - ohne Namen und Klartext läse ein Screenreader nur eine kontextlose
     * Zahl vor. Der Name steht deshalb im {@code aria-label}, und {@code aria-valuetext} nennt
     * dieselbe Restzeit im selben Format wie die sichtbare Kennzahl daneben. Beides ist nicht
     * sichtbar, der angezeigte Text ändert sich dadurch nicht.
     */
    private static ProgressBar buildRemainingProgress(ExecutionEntity execution, Duration remainingTime) {
        ProgressBar progress = new ProgressBar();
        progress.addClassName("device-progress");
        progress.setValue(progressOf(elapsedOf(execution).getSeconds(),
                execution.getProgram().getMaxDurationSeconds()));
        progress.getElement().setAttribute("aria-label", "Fortschritt");
        progress.getElement().setAttribute("aria-valuetext", "Restzeit " + formatDuration(remainingTime));
        return progress;
    }

    /**
     * Der Füllgrad des Fortschrittsbalkens: verstrichene Zeit im Verhältnis zur Höchstdauer des
     * Programms, als Anteil zwischen 0 und 1 (der Wertebereich, den {@link ProgressBar} ohne
     * gesetztes Minimum/Maximum erwartet).
     *
     * <p>Randfälle: Läuft eine Ausführung über ihre Höchstdauer hinaus (verspätete Endmeldung
     * eines Terminals), bleibt der Balken bei 100% stehen statt überzulaufen - dieselbe
     * Deckelung, die {@code ExecutionService#getPrice} für die Abrechnung vornimmt. Ohne
     * Höchstdauer (0) gibt es keinen Bezugspunkt für einen Fortschritt; der Balken steht dann
     * voll, passend zur Restzeit 00:00:00 daneben. Eine Ausführung ohne Startzeitpunkt liefert
     * über {@link #elapsedOf} 0 und damit einen leeren Balken.
     *
     * <p>Als reine Funktion herausgezogen (paketsichtbar), damit die Klemmlogik ohne UI testbar
     * ist - dasselbe Muster wie {@code AbstractFormDialog#failureText}.
     */
    static double progressOf(long elapsedSeconds, int maxDurationSeconds) {
        if (maxDurationSeconds <= 0) {
            return 1.0;
        }
        return Math.clamp((double) elapsedSeconds / maxDurationSeconds, 0.0, 1.0);
    }

    /**
     * Überschrift "Verlauf" mit der Anzahl der Einträge über der Historie (UI-Redesign v2). Die
     * Zahl ist dieselbe, die das lazy geladene Grid für seine Bildlaufhöhe braucht (siehe
     * {@link #populateDevicePanel}) - sie sagt dem Administrator, wie viel unterhalb des
     * sichtbaren Ausschnitts noch folgt.
     */
    private static Div buildHistoryHeader(long count) {
        Div header = new Div();
        header.addClassName("history-header");
        // Die Typografie der Überschrift steht auf .history-header (siehe portal-theme.css),
        // das Label braucht deshalb keine eigene Klasse.
        Span title = new Span("Verlauf");
        Span countLabel = new Span(count == 1 ? "1 Eintrag" : count + " Einträge");
        countLabel.addClassName("history-count");
        header.add(title, countLabel);
        return header;
    }

    private Grid<ExecutionEntity> buildHistoryGrid(DeviceStatus deviceStatus, long historyCount) {
        DeviceEntity device = deviceStatus.device();
        Grid<ExecutionEntity> grid = new Grid<>();
        grid.addClassName("dashboard-device-history");
        // UI-Redesign v2: kompakte Zeilen, damit unter der Gerätekarte mehr Verlauf in dieselbe
        // Höhe passt.
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setHeight("14em");
        grid.setWidthFull();

        // Sortierbar sind genau die Spalten, die die Datenbank auch wirklich sortieren kann
        // (UI-Redesign v2): das Grid lädt seitenweise (siehe unten), eine Sortierung muss also
        // in die Abfrage gehen. "Dauer" und "Preis" stehen in keiner Spalte der Tabelle - die
        // Dauer ergibt sich aus start/stop bzw. der laufenden Uhr, der Preis wird je Zeile aus
        // Programm, Benutzergruppe und Dauer berechnet (ExecutionService#getPrice). Sie bleiben
        // deshalb bewusst unsortierbar, statt eine Sortierung vorzutäuschen, die nur die gerade
        // geladene Seite ordnet.
        Grid.Column<ExecutionEntity> dateColumn = grid.addColumn(e -> PortalFormats.dateTime(e.getStart()))
                .setHeader("Datum").setSortable(true).setSortProperty("start");
        grid.addColumn(e -> e.getUser() == null ? "-" : e.getUser().getName()).setHeader("Benutzer").setSortable(true)
                .setSortProperty("user.name");
        // Tabellarische Ziffern auf Dauer und Preis, damit die Zahlen spaltenweise untereinander
        // stehen. setClassNameGenerator ist in Vaadin 24.10 zugunsten von Shadow-DOM-Parts als
        // veraltet markiert - hier bewusst weiter verwendet, weil das Portal-Theme die Zellen
        // (die als Light-DOM-Elemente vorliegen) über CSS-Klassen anspricht, nicht über ::part.
        grid.addColumn(e -> formatDuration(elapsedOf(e))).setHeader("Dauer")
                .setClassNameGenerator(e -> "tabular-nums");
        grid.addColumn(e -> PortalFormats.currency(this.executionService.getPrice(e))).setHeader("Preis")
                .setClassNameGenerator(e -> "tabular-nums");

        grid.setPartNameGenerator(e -> {
            if (!e.isFinished() && e.getStart() != null) {
                return this.executionService.isExpired(e) ? "expired-execution" : "running-execution";
            }
            return null;
        });

        // Issue #30 (Pre-Launch AP5): lazy, seitenweise geladene Historie (neueste zuerst) statt
        // der vollständigen Liste. Der Preis (N+1 über lazy program/user/group) wird damit nur
        // noch für die tatsächlich sichtbaren Zeilen berechnet, nicht für die gesamte Historie.
        // Die Gesamtzahl ist die beim Panel-Aufbau bereits ermittelte (siehe
        // populateDevicePanel); sie bleibt aktuell, weil jedes Ereignis am Gerät das ganze
        // Panel samt Grid neu aufbaut.
        grid.setItems(
                query -> this.executionService.getExecutions(device,
                        PageRequest.of(query.getPage(), query.getPageSize(),
                                toHistorySort(query.getSortOrders()))).stream(),
                query -> (int) historyCount);
        // Anfangszustand: neueste zuerst - wie bisher, nur jetzt auch als Pfeil im Spaltenkopf
        // sichtbar und damit umkehrbar.
        grid.sort(List.of(new GridSortOrder<>(dateColumn, SortDirection.DESCENDING)));
        return grid;
    }

    /**
     * Übersetzt die Sortierung der Spaltenköpfe (UI-Redesign v2) in eine Spring-Data-Sortierung
     * für die seitenweise Abfrage. Ohne angeklickten Spaltenkopf bleibt es bei der bisherigen
     * Voreinstellung "neueste zuerst".
     *
     * <p>Stabiler Zweit-Sortierschlüssel (id DESC, seit Issue #30): Bei Lazy-Pagination stellt der
     * Callback pro Seite eine eigene SQL-Abfrage. Teilten sich zwei Ausführungen denselben
     * Sortierwert (realistisch bei importierten/Demo-Daten oder beim Sortieren nach Benutzer),
     * könnte Postgres sie über die Seiten hinweg in unterschiedlicher Reihenfolge liefern - eine
     * Zeile erschiene sonst doppelt oder gar nicht. Er hängt deshalb an JEDER Sortierung, nicht
     * nur an der Voreinstellung.
     *
     * <p>Paketsichtbar, damit die Übersetzung ohne UI testbar ist - dasselbe Muster wie
     * {@code AbstractFormDialog#failureText}.
     */
    static Sort toHistorySort(List<QuerySortOrder> sortOrders) {
        List<Sort.Order> orders = new ArrayList<>();
        for (QuerySortOrder sortOrder : sortOrders) {
            orders.add(new Sort.Order(
                    sortOrder.getDirection() == SortDirection.DESCENDING ? Sort.Direction.DESC : Sort.Direction.ASC,
                    sortOrder.getSorted()));
        }
        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.DESC, "start"));
        }
        orders.add(new Sort.Order(Sort.Direction.DESC, "id"));
        return Sort.by(orders);
    }

    private static Duration elapsedOf(ExecutionEntity execution) {
        if (execution.getStart() == null) {
            return Duration.ZERO;
        }
        LocalDateTime end = execution.getStop() != null ? execution.getStop() : LocalDateTime.now();
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
}
