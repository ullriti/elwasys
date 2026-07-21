package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.CreditService;

/**
 * Modaler, rein lesender Dialog mit der vollständigen Buchungshistorie eines Benutzers
 * ("Umsätze ansehen") - fachlicher Nachfolger von
 * {@code Portal/.../components/CreditAccountingWindow} (Alt-Portal). Zeigt
 * {@link CreditService#getAccountingEntries} unverändert an (neueste zuerst); da Buchungen
 * unveränderlich sind (siehe kb/02-data-model.md), bietet dieser Dialog bewusst KEINE
 * Bearbeitungs- oder Löschfunktion - nur eine Liste, identisch zum Alt-Fenster.
 */
public class CreditHistoryDialog extends Dialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.GERMANY);

    public CreditHistoryDialog(CreditService creditService, UserEntity user) {
        setHeaderTitle("Umsätze von " + user.getName());
        setModal(true);
        setResizable(true);
        setWidth("50em");
        setHeight("35em");

        Grid<CreditAccountingEntryEntity> grid = new Grid<>();
        grid.setSizeFull();
        grid.addColumn(e -> e.getDate().format(DATE_FORMAT)).setHeader("Datum").setFlexGrow(0).setWidth("12em");
        grid.addColumn(this::formatAmount).setHeader("Betrag").setFlexGrow(0).setWidth("8em");
        grid.addColumn(CreditAccountingEntryEntity::getDescription).setHeader("Buchungstext");
        grid.setItems(creditService.getAccountingEntries(user));

        add(grid);
    }

    private String formatAmount(CreditAccountingEntryEntity entry) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(entry.getAmount());
    }
}
