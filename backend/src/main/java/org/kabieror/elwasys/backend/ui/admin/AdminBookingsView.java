package org.kabieror.elwasys.backend.ui.admin;

import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import java.math.BigDecimal;
import java.util.List;
import org.kabieror.elwasys.backend.domain.BookingType;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.ui.component.ListFilterField;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;

/**
 * Buchungsübersicht: alle Guthaben-Buchungen aller Benutzer, filterbar nach Art (Einzahlungen,
 * Auszahlungen, Programmabrechnungen) und Zeitraum. Neu gegenüber dem Alt-Portal, das Buchungen
 * nur je Benutzer zeigte ("Umsätze ansehen", siehe {@code CreditHistoryDialog}).
 *
 * <p>Rein lesend - Buchungen sind unveränderlich (siehe {@link CreditAccountingEntryEntity}). Art
 * und Zeitraum werden in der Datenbank gefiltert ({@link CreditService#findBookings}), der
 * Freitext ({@link ListFilterField}) wie in den übrigen Listen clientnah auf den geladenen Zeilen.
 * Kein Live-Update: die Liste ist beim Aufruf und nach jeder Filteränderung aktuell.
 */
@Route(value = "admin/bookings", layout = AdminLayout.class)
@PageTitle("Buchungen - Waschportal")
@RolesAllowed("ADMIN")
public class AdminBookingsView extends VerticalLayout {

    /** Auswahlwert für "keine Einschränkung" - Vaadins Select kennt kein null als Eintrag. */
    private static final String ALL = "Alle Buchungen";

    private final CreditService creditService;

    private final Grid<CreditAccountingEntryEntity> grid = new Grid<>();
    private final Select<String> typeSelect = new Select<>();
    private final DatePicker fromPicker = new DatePicker("Von");
    private final DatePicker toPicker = new DatePicker("Bis");
    private final Span summary = new Span();
    private final ListFilterField<CreditAccountingEntryEntity> filterField =
            new ListFilterField<>("Buchungen filtern");

    public AdminBookingsView(CreditService creditService) {
        this.creditService = creditService;

        setSizeFull();
        addClassName("admin-bookings-view");

        H2 title = new H2("Buchungen");

        this.typeSelect.setLabel("Art");
        this.typeSelect.setItems(ALL, BookingType.INPAYMENT.getLabel(), BookingType.PAYOUT.getLabel(),
                BookingType.EXECUTION.getLabel());
        this.typeSelect.setValue(ALL);
        this.typeSelect.addValueChangeListener(e -> loadData());
        this.fromPicker.addValueChangeListener(e -> loadData());
        this.toPicker.addValueChangeListener(e -> loadData());
        for (DatePicker picker : List.of(this.fromPicker, this.toPicker)) {
            picker.setClearButtonVisible(true);
            picker.setLocale(java.util.Locale.GERMANY);
        }

        HorizontalLayout toolbar = new HorizontalLayout(title, this.typeSelect, this.fromPicker, this.toPicker,
                this.filterField);
        toolbar.addClassName("list-toolbar");
        toolbar.setWidthFull();
        toolbar.setFlexGrow(1, title);
        toolbar.setAlignItems(FlexComponent.Alignment.END);

        this.summary.addClassName("bookings-summary");

        configureGrid();
        this.filterField.bindTo(this.grid, AdminBookingsView::filterableText);

        add(toolbar, this.summary, this.grid);
        setFlexGrow(1, this.grid);

        loadData();
    }

    private void configureGrid() {
        this.grid.setSizeFull();
        // Zeitspalte sortiert auf dem echten LocalDateTime, nicht auf dem formatierten Text.
        this.grid.addColumn(e -> PortalFormats.dateTime(e.getDate())).setHeader("Datum").setFlexGrow(0)
                .setAutoWidth(true).setSortable(true).setComparator(CreditAccountingEntryEntity::getDate);
        this.grid.addColumn(e -> e.getUser().getName()).setHeader("Benutzer").setFlexGrow(0).setAutoWidth(true)
                .setSortable(true).setComparator(e -> e.getUser().getName());
        this.grid.addColumn(AdminBookingsView::typeLabel).setHeader("Art").setFlexGrow(0).setAutoWidth(true)
                .setSortable(true).setComparator(AdminBookingsView::typeLabel);
        this.grid.addComponentColumn(AdminBookingsView::amountLabel).setHeader("Betrag").setFlexGrow(0)
                .setAutoWidth(true).setSortable(true).setComparator(CreditAccountingEntryEntity::getAmount);
        this.grid.addColumn(e -> e.getDescription() == null ? "" : e.getDescription()).setHeader("Buchungstext");
    }

    private static String typeLabel(CreditAccountingEntryEntity entry) {
        BookingType type = BookingType.of(entry);
        // Eine Buchung über 0,00 ohne Ausführung gehört zu keiner Art (kommt praktisch nicht vor).
        return type == null ? "Sonstige" : type.getLabel();
    }

    /** Betrag mit Vorzeichen-Farbe (grün = Gutschrift, rot = Belastung), siehe portal-theme.css. */
    private static Span amountLabel(CreditAccountingEntryEntity entry) {
        Span amount = new Span(PortalFormats.currency(entry.getAmount()));
        amount.addClassNames("booking-amount",
                entry.getAmount().signum() >= 0 ? "booking-amount-credit" : "booking-amount-debit");
        return amount;
    }

    private void loadData() {
        BookingType type = selectedType();
        List<CreditAccountingEntryEntity> entries = this.creditService.findBookings(type,
                this.fromPicker.getValue(), this.toPicker.getValue());
        this.grid.setItems(entries);
        // autoWidth misst erst, wenn Zellinhalt da ist (siehe AdminOfflineIncidentsView).
        this.grid.recalculateColumnWidths();
        this.filterField.reapply();
        this.summary.setText(summaryText(entries));
    }

    private BookingType selectedType() {
        String selected = this.typeSelect.getValue();
        for (BookingType type : BookingType.values()) {
            if (type.getLabel().equals(selected)) {
                return type;
            }
        }
        return null;
    }

    /** Anzahl und Summen der geladenen (nicht des per Freitext weiter eingeschränkten) Buchungen. */
    static String summaryText(List<CreditAccountingEntryEntity> entries) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        for (CreditAccountingEntryEntity entry : entries) {
            if (entry.getAmount().signum() >= 0) {
                credits = credits.add(entry.getAmount());
            } else {
                debits = debits.add(entry.getAmount());
            }
        }
        return entries.size() + (entries.size() == 1 ? " Buchung" : " Buchungen") + " · Gutschriften "
                + PortalFormats.currency(credits) + " · Belastungen " + PortalFormats.currency(debits)
                + " · Saldo " + PortalFormats.currency(credits.add(debits));
    }

    private static String filterableText(CreditAccountingEntryEntity entry) {
        return String.join(" ", PortalFormats.dateTime(entry.getDate()), entry.getUser().getName(),
                typeLabel(entry), PortalFormats.currency(entry.getAmount()),
                entry.getDescription() == null ? "" : entry.getDescription());
    }
}
