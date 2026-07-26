package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import java.util.Comparator;
import java.util.List;
import org.kabieror.elwasys.backend.auth.ElwasysUserPrincipal;
import org.kabieror.elwasys.backend.domain.TerminalOfflineIncidentEntity;
import org.kabieror.elwasys.backend.service.TerminalOfflineIncidentService;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;

/**
 * Offline-Vorfälle der Terminals (Issue #89, Dead-Letter-Sichtbarkeit).
 *
 * <p>Die Terminals melden über den Wartungs-WebSocket zwei Arten von Vorfällen des
 * Offline-Pfads, die beide Geld betreffen (siehe {@link TerminalOfflineIncidentEntity}): eine
 * endgültig abgelehnte Offline-Buchung ("verlorene Buchung", {@code KIND_DEAD_LETTER}) und eine
 * nach fehlgeschlagener Kompensation offen gebliebene Ausführung ("Geister-Ausführung",
 * {@code KIND_GHOST_EXECUTION}). Solange ein Vorfall nicht quittiert ist, meldet der
 * {@code OfflineIncidentHealthIndicator} über {@code /actuator/health/operational} Alarm - <b>diese
 * Ansicht ist der einzige Weg, den Alarm wieder zu beenden</b> (bewusst: ein Geldverlust soll
 * aktiv zur Kenntnis genommen werden, statt still zu verfallen).
 *
 * <p><b>Sichtbarkeit</b>: standardmäßig zeigt die Liste nur die OFFENEN Vorfälle - das ist genau
 * die Menge, die den Alarm hält, und damit die Arbeitsliste des Administrators. Quittierte
 * Vorfälle bleiben über den Umschalter "Auch quittierte Vorfälle anzeigen" einsehbar: sie werden
 * nie gelöscht (kein Purge, siehe Migration {@code V12}), weil sie den Beleg über einen
 * tatsächlich eingetretenen Geldverlust darstellen.
 *
 * <p><b>Kein Live-Update</b>: anders als die Stammdaten-Views (siehe {@code AdminUsersView},
 * Phase 3 AP5) hängt diese Ansicht nicht am {@code UiBroadcaster} - für Offline-Vorfälle gibt es
 * kein {@code DomainEvent}, sie treffen asynchron über den WebSocket ein. Der Alarmpfad ist der
 * Health-Endpunkt, nicht das offene Browserfenster; die Liste ist beim Seitenaufruf und nach
 * jeder Quittierung aktuell.
 */
@Route(value = "admin/offline-incidents", layout = AdminLayout.class)
@PageTitle("Offline-Vorfälle - Waschportal")
@RolesAllowed("ADMIN")
public class AdminOfflineIncidentsView extends VerticalLayout {

    private final TerminalOfflineIncidentService incidentService;
    private final String actingAdminName;

    private final Grid<TerminalOfflineIncidentEntity> grid = new Grid<>();
    private final Checkbox showAcknowledged = new Checkbox("Auch quittierte Vorfälle anzeigen");
    private final Span openCountBadge = new Span();

    public AdminOfflineIncidentsView(TerminalOfflineIncidentService incidentService,
            AuthenticationContext authenticationContext) {
        this.incidentService = incidentService;
        // Wie in AdminUsersView: der angemeldete Portal-Benutzer wird namentlich in der Buchung
        // bzw. hier in der Quittierung festgehalten (Beleg, wer den Verlust zur Kenntnis nahm).
        this.actingAdminName = authenticationContext.getAuthenticatedUser(ElwasysUserPrincipal.class)
                .map(ElwasysUserPrincipal::getName).orElse(authenticationContext.getPrincipalName().orElse(""));

        setSizeFull();
        addClassName("admin-offline-incidents-view");

        H2 title = new H2("Offline-Vorfälle");
        this.showAcknowledged.addValueChangeListener(e -> loadData());

        HorizontalLayout toolbar = new HorizontalLayout(title, this.openCountBadge, this.showAcknowledged);
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, title);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);

        configureGrid();

        add(toolbar, buildExplanation(), this.grid);
        setFlexGrow(1, this.grid);

        loadData();
    }

    /** Erklärt den fachlichen Ernst des Vorfalls - wie der Erklärtext im ExpiredExecutionsDialog. */
    private static Paragraph buildExplanation() {
        Paragraph explanation = new Paragraph(
                "Diese Vorfälle hat ein Terminal aus seinem Offline-Betrieb gemeldet. Eine verlorene Buchung ist "
                        + "eine Programmausführung, die das Terminal offline abgerechnet hat, die das Backend aber "
                        + "endgültig abgelehnt hat - der Betrag wurde also nie gutgeschrieben oder belastet. Eine "
                        + "Geister-Ausführung ist eine Ausführung, die nach einem fehlgeschlagenen Abbruch offen "
                        + "geblieben ist. Solange ein Vorfall nicht quittiert ist, meldet die Betriebsüberwachung "
                        + "Alarm; die Quittierung beendet den Alarm, der Vorfall bleibt als Beleg erhalten.");
        explanation.addClassName("small");
        return explanation;
    }

    private void configureGrid() {
        this.grid.setSizeFull();

        this.grid.addColumn(i -> PortalFormats.dateTime(i.getOccurredAt())).setHeader("Aufgetreten").setFlexGrow(0)
                .setWidth("10em");
        this.grid.addColumn(i -> PortalFormats.dateTime(i.getReportedAt())).setHeader("Gemeldet").setFlexGrow(0)
                .setWidth("10em");
        this.grid.addColumn(i -> i.getLocation().getName()).setHeader("Standort").setFlexGrow(0).setWidth("9em");
        this.grid.addColumn(i -> kindLabel(i.getKind())).setHeader("Art").setFlexGrow(0).setWidth("11em");
        // Der Nutzer ist informativ und kann fehlen (nie gemeldet oder zwischenzeitlich gelöscht -
        // der Vorfall bleibt trotzdem bestehen, siehe TerminalOfflineIncidentService#report).
        this.grid.addColumn(i -> i.getUser() == null ? "-" : i.getUser().getName()).setHeader("Benutzer")
                .setFlexGrow(0).setWidth("11em");
        this.grid.addComponentColumn(AdminOfflineIncidentsView::amountLabel).setHeader("Betrag").setFlexGrow(0)
                .setWidth("7em");
        this.grid.addColumn(TerminalOfflineIncidentEntity::getReason).setHeader("Grund");
        this.grid.addComponentColumn(AdminOfflineIncidentsView::statusBadge).setHeader("Status").setFlexGrow(0)
                .setWidth("9em");
        this.grid.addComponentColumn(this::actionButtons).setHeader("").setFlexGrow(0).setWidth("120px");
    }

    /** Verständliche Bezeichnung statt des rohen {@code kind}-Schlüssels aus der Meldung. */
    private static String kindLabel(String kind) {
        if (TerminalOfflineIncidentEntity.KIND_DEAD_LETTER.equals(kind)) {
            return "Verlorene Buchung";
        }
        if (TerminalOfflineIncidentEntity.KIND_GHOST_EXECUTION.equals(kind)) {
            return "Geister-Ausführung";
        }
        // Eine (künftige) unbekannte Art lieber roh anzeigen als verschlucken.
        return kind == null ? "-" : kind;
    }

    /**
     * Der Schaden - bewusst hervorgehoben (siehe portal-theme.css {@code .incident-amount}): er
     * ist die eigentliche Information dieser Ansicht.
     */
    private static Span amountLabel(TerminalOfflineIncidentEntity incident) {
        Span amount = new Span(PortalFormats.currency(incident.getChargedPrice()));
        amount.addClassName("incident-amount");
        return amount;
    }

    private static Span statusBadge(TerminalOfflineIncidentEntity incident) {
        if (!incident.isAcknowledged()) {
            Span badge = new Span("Offen");
            badge.getElement().getThemeList().add("badge error");
            return badge;
        }
        Span badge = new Span("Quittiert");
        badge.getElement().getThemeList().add("badge success");
        badge.setTitle("Quittiert von " + incident.getAcknowledgedBy() + " am "
                + PortalFormats.dateTime(incident.getAcknowledgedAt()));
        return badge;
    }

    private Component actionButtons(TerminalOfflineIncidentEntity incident) {
        if (incident.isAcknowledged()) {
            // Ein quittierter Vorfall ist nur noch Beleg - es gibt keine Aktion mehr darauf.
            return new Span();
        }
        Button btnAcknowledge = new Button("Quittieren", e -> confirmAcknowledge(incident));
        btnAcknowledge.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        // Issue #49: Doppelklick-Schutz wie auf den geldbewegenden Knöpfen im Portal.
        btnAcknowledge.setDisableOnClick(true);
        return btnAcknowledge;
    }

    /**
     * Die Quittierung ist die bewusste Kenntnisnahme eines Geldverlusts und beendet den
     * Betriebsalarm - deshalb läuft sie, wie die Löschpfade des Portals, über eine ausdrückliche
     * Bestätigung (gemeinsamer Ja/Nein-Dialog, siehe {@link ConfirmDeleteDialog}).
     */
    private void confirmAcknowledge(TerminalOfflineIncidentEntity incident) {
        String amount = incident.getChargedPrice() == null ? ""
                : " Betroffener Betrag: " + PortalFormats.currency(incident.getChargedPrice()) + ".";
        ConfirmDeleteDialog.show("Vorfall quittieren",
                "Möchten Sie diesen Vorfall wirklich als zur Kenntnis genommen quittieren?" + amount
                        + " Der Betriebsalarm endet damit; der Vorfall bleibt als Beleg erhalten.",
                () -> acknowledge(incident));
    }

    private void acknowledge(TerminalOfflineIncidentEntity incident) {
        this.incidentService.acknowledge(incident.getId(), this.actingAdminName);
        Notification notification = Notification.show("Der Vorfall wurde quittiert.", 4000,
                Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        loadData();
    }

    private void loadData() {
        List<TerminalOfflineIncidentEntity> incidents;
        if (this.showAcknowledged.getValue()) {
            // findAll() liefert unsortiert - hier dieselbe Reihenfolge herstellen wie findOpen()
            // (neueste Meldung zuerst), damit der Umschalter die Liste nicht umsortiert.
            incidents = this.incidentService.findAll().stream()
                    .sorted(Comparator.comparing(TerminalOfflineIncidentEntity::getReportedAt).reversed()).toList();
        } else {
            incidents = this.incidentService.findOpen();
        }
        this.grid.setItems(incidents);

        // Ohne den Umschalter enthält die Liste genau die offenen Vorfälle - dann ist eine
        // zusätzliche Zählabfrage überflüssig.
        long openCount = this.showAcknowledged.getValue() ? this.incidentService.countOpen() : incidents.size();
        this.openCountBadge.setText(openCount == 0 ? "Keine offenen Vorfälle"
                : openCount == 1 ? "1 offener Vorfall" : openCount + " offene Vorfälle");
        this.openCountBadge.getElement().getThemeList().clear();
        this.openCountBadge.getElement().getThemeList().add(openCount == 0 ? "badge success" : "badge error");
    }
}
