package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextArea;
import java.util.List;

/**
 * Dialog "Log" (Phase 3 AP4, siehe kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code Portal/.../components/LogViewerWindow} (Alt-Portal): schreibgeschützte Anzeige des
 * über {@code TerminalMaintenanceService#requestLog} vom Terminal geholten Log-Inhalts, eine
 * Zeile pro Listeneintrag, mit Zeilenumbrüchen verbunden - 1:1 wie im Alt-Fenster.
 */
public class LogViewerDialog extends Dialog {

    public LogViewerDialog(List<String> logLines) {
        setHeaderTitle("Log");
        setModal(true);
        setResizable(true);
        setWidth("70em");
        setHeight("40em");

        TextArea logArea = new TextArea();
        logArea.setSizeFull();
        logArea.setValue(String.join("\n", logLines));
        logArea.setReadOnly(true);
        logArea.addClassName("log-textfield");
        add(logArea);

        Button btnClose = new Button("Schließen", e -> close());
        getFooter().add(btnClose);
    }
}
