package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/**
 * Kleiner, wiederverwendbarer Bestätigungsdialog fürs Löschen einer Stammdaten-Entität -
 * fachlicher Nachfolger von {@code Portal/.../components/ConfirmWindow} (Alt-Portal, dort
 * u.a. für Benutzer-/Benutzergruppen-Löschung verwendet, siehe Testfall P13). Nutzt Vaadins
 * eingebauten {@link ConfirmDialog} statt eines selbst gebauten modalen Fensters - fachlich
 * identisches Verhalten (Ja/Nein, Aktion läuft erst nach Bestätigung), moderner Baustein.
 */
public final class ConfirmDeleteDialog {

    private ConfirmDeleteDialog() {
    }

    /**
     * Zeigt einen Bestätigungsdialog mit deutschen "Ja"/"Nein"-Beschriftungen (1:1 wie das
     * Alt-Portal-{@code ConfirmWindow}). {@code onConfirm} läuft nur, wenn "Ja" gewählt
     * wurde.
     */
    public static void show(String title, String question, Runnable onConfirm) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(title);
        dialog.setText(question);
        dialog.setCancelable(true);
        dialog.setCancelText("Nein");
        dialog.setConfirmText("Ja");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> onConfirm.run());
        dialog.open();
    }
}
