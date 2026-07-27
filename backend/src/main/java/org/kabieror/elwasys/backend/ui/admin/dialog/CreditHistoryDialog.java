package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;

/**
 * Modaler, rein lesender Dialog mit der vollständigen Buchungshistorie eines Benutzers
 * ("Umsätze ansehen") - fachlicher Nachfolger von
 * {@code Portal/.../components/CreditAccountingWindow} (Alt-Portal). Zeigt
 * {@link CreditService#getAccountingEntries} unverändert an (neueste zuerst); da Buchungen
 * unveränderlich sind (siehe docs/kb/02-data-model.md), bietet dieser Dialog bewusst KEINE
 * Bearbeitungs- oder Löschfunktion - nur eine Liste, identisch zum Alt-Fenster.
 */
public class CreditHistoryDialog extends Dialog {

    public CreditHistoryDialog(CreditService creditService, UserEntity user) {
        setHeaderTitle("Umsätze von " + user.getName());
        setModal(true);
        setResizable(true);
        setWidth("50em");
        setHeight("35em");
        // UI-Redesign v2: dasselbe Schließen-Kreuz wie in den Formular-Dialogen (dieser Dialog
        // erbt bewusst nicht von AbstractFormDialog, siehe dortiges Klassen-Javadoc).
        AbstractFormDialog.addCloseButton(this);

        Grid<CreditAccountingEntryEntity> grid = new Grid<>();
        grid.setSizeFull();
        // Breiten in rem, nicht em: der Versalien-Kopf des Designs v2 steht in 0.75rem, eine
        // em-Breite ergaebe dort schmalere Spalten als im Koerper und die Kopfzeile liefe
        // gegenueber ihren Daten weg (siehe Kommentar in portal-theme.css).
        grid.addColumn(e -> PortalFormats.dateTime(e.getDate())).setHeader("Datum").setFlexGrow(0).setWidth("12rem");
        grid.addColumn(e -> PortalFormats.currency(e.getAmount())).setHeader("Betrag").setFlexGrow(0).setWidth("8rem");
        grid.addColumn(CreditAccountingEntryEntity::getDescription).setHeader("Buchungstext");
        grid.setItems(creditService.getAccountingEntries(user));

        add(grid);
    }
}
