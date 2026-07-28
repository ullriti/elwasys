package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import java.math.BigDecimal;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.exception.NotEnoughCreditException;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.FormValidation;
import org.kabieror.elwasys.backend.ui.component.Notifications;

/**
 * Modaler Dialog zum Verändern des Guthabens eines Benutzers ("Guthaben aufladen") - fachlicher
 * Nachfolger von {@code Portal/.../components/UserCreditWindow} (Alt-Portal, Testfall P8).
 * Nutzt ausschließlich die bestehenden Phase-2-Methoden {@link CreditService#inpayment} und
 * {@link CreditService#payout}: dieser Dialog erzeugt damit ausnahmslos NEUE Buchungssätze,
 * er verändert oder löscht nie einen bestehenden Eintrag in {@code credit_accounting} - die
 * Unveränderlichkeit der Buchungen (Rahmenbedingung, siehe docs/kb/02-data-model.md) ist damit
 * strukturell sichergestellt.
 *
 * <p>Felder und Vorbelegung wie im Alt-Fenster: Einzahlung/Auszahlung (Radiobuttons, Default
 * "Einzahlung"), Betrag, Buchungstext (vorbelegt mit "Einzahlung vom Waschportal von
 * &lt;Admin&gt;" bzw. "Auszahlung vom Waschportal von &lt;Admin&gt;" - der Alt-Code nutzt dafür
 * den Namen des angemeldeten Administrators, hier über {@code actingAdminName} vom Aufrufer
 * übergeben statt eines statischen Session-Singletons). Ein Wechsel der Buchungsart
 * überschreibt den Buchungstext nur, wenn er noch dem jeweils anderen Standardtext entspricht
 * oder leer ist - 1:1 wie {@code UserCreditWindow}.
 */
public class CreditTopUpDialog extends AbstractFormDialog {

    private static final String INPAYMENT_OPTION = "Einzahlung";
    private static final String PAYOUT_OPTION = "Auszahlung";

    private final CreditService creditService;
    private final UserEntity user;

    private final RadioButtonGroup<String> rgAction = new RadioButtonGroup<>();
    private final BigDecimalField bfAmount = new BigDecimalField("Betrag");
    private final TextField tfText = new TextField("Buchungstext");

    public CreditTopUpDialog(CreditService creditService, UserEntity user, String actingAdminName,
            Runnable onSaved) {
        super("Guthaben von " + user.getName(), "28em");
        this.creditService = creditService;
        this.user = user;

        String inpaymentText = "Einzahlung vom Waschportal von " + actingAdminName;
        String payoutText = "Auszahlung vom Waschportal von " + actingAdminName;

        this.rgAction.setLabel("Aktion");
        this.rgAction.setItems(INPAYMENT_OPTION, PAYOUT_OPTION);
        this.rgAction.setValue(INPAYMENT_OPTION);
        this.rgAction.addValueChangeListener(e -> {
            if (INPAYMENT_OPTION.equals(e.getValue())) {
                if (this.tfText.isEmpty() || this.tfText.getValue().equals(payoutText)) {
                    this.tfText.setValue(inpaymentText);
                }
            } else if (this.tfText.isEmpty() || this.tfText.getValue().equals(inpaymentText)) {
                this.tfText.setValue(payoutText);
            }
        });

        this.bfAmount.setWidthFull();
        // UI-Redesign v2: Währungszeichen als Feld-Suffix; das Feldlabel ("Betrag") bleibt
        // unverändert.
        this.bfAmount.setSuffixComponent(new Span("€"));

        this.tfText.setValue(inpaymentText);
        this.tfText.setWidthFull();

        FormLayout form = new FormLayout(this.rgAction, this.bfAmount, this.tfText);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        addToBody(form);

        // Issue #49: Doppelklick-Schutz auf dem geldbewegenden "Buchen"-Knopf - er blockiert eine
        // Doppelbuchung aus zwei Klicks im selben UIDL-Batch bei langsamem Roundtrip. Das
        // Zurücksetzen danach ist NICHT selbstverständlich (siehe PortalButtons): ohne es wäre
        // "Buchen" nach einer fehlgeschlagenen Validierung tot und der Betrag ließe sich nicht
        // mehr korrigieren - der Dialog bleibt in diesem Fall ja offen.
        addFooterActions("Buchen", () -> execute(onSaved), true);

        focusOnOpen(this.bfAmount);
    }

    private void execute(Runnable onSaved) {
        if (!FormValidation.require(this.bfAmount, "Bitte einen Betrag eingeben.")) {
            return;
        }

        BigDecimal amount = this.bfAmount.getValue();
        // Issue #22: nur positive Beträge zulassen. Ein negativer Betrag kehrte sonst die
        // Buchung um (eine "Einzahlung" von -50 umgeht den Auszahlungs-Wächter, eine
        // "Auszahlung" von -50 bucht +50 mit widersprüchlichem Buchungstext), 0 erzeugte einen
        // leeren Buchungssatz. CreditService#inpayment/#payout prüfen dies zusätzlich
        // server-seitig, hier die unmittelbare Feld-Rückmeldung.
        if (!FormValidation.check(amount.signum() > 0, this.bfAmount, "Der Betrag muss größer als 0 sein.")) {
            return;
        }

        boolean payout = PAYOUT_OPTION.equals(this.rgAction.getValue());
        try {
            if (payout) {
                this.creditService.payout(this.user, amount, this.tfText.getValue());
            } else {
                this.creditService.inpayment(this.user, amount, this.tfText.getValue());
            }
        } catch (NotEnoughCreditException e) {
            Notifications.showError("Das Guthaben des Benutzers reicht nicht aus für diese Operation.");
            return;
        }

        close();
        onSaved.run();
    }

}
