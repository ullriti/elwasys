package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.button.Button;

/**
 * Der Doppelklick-Schutz der unmittelbar handelnden Schaltflächen des Portals (Issue #49) - an
 * genau einer Stelle, samt der Falle, die er mitbringt.
 *
 * <p><b>Was Vaadins {@code setDisableOnClick} tut</b> (nachgesehen in 24.10,
 * {@code DisableOnClickController}): es setzt am Element das Attribut {@code disableonclick} -
 * das Web-Component deaktiviert sich damit clientseitig SOFORT beim Klick, noch vor dem
 * Server-Roundtrip, und genau das ist der Schutz gegen den Doppelklick - und es hängt zusätzlich
 * einen Klick-Listener ein, der die Schaltfläche auch serverseitig auf {@code disabled} setzt.
 * Dieser Listener wird bereits im Konstruktor des Knopfs registriert und läuft deshalb VOR allen
 * Aktions-Listenern.
 *
 * <p><b>Was es NICHT tut</b>: die Schaltfläche nach dem Roundtrip wieder aktivieren - sie bleibt
 * deaktiviert, bis jemand {@code setEnabled(true)} ruft. Bleibt der Dialog bzw. die Ansicht nach
 * der Aktion offen (fehlgeschlagene Validierung, gemeldeter Fehler, oder eine schlicht
 * wiederholbare Aktion), sitzt der Bediener vor einem toten Knopf und kommt nur über ein
 * Neuladen wieder heraus. Diese Annahme war im Portal dreimal falsch dokumentiert und zweimal
 * ein echter Bedienbarkeits-Bug ({@code CreditTopUpDialog}, {@code ExpiredExecutionsDialog}).
 *
 * <p>Deshalb dieser Helfer: Aktion und Zurücksetzen gehören zusammen, und das Zurücksetzen steht
 * im {@code finally}-Zweig - auch eine Ausnahme aus der Aktion darf die Schaltfläche nicht tot
 * zurücklassen.
 */
public final class PortalButtons {

    private PortalButtons() {
        // Utility-Klasse
    }

    /**
     * Hängt die Aktion an die Schaltfläche und rüstet sie mit dem Doppelklick-Schutz aus: während
     * des Server-Roundtrips ist sie deaktiviert, danach wieder bedienbar.
     *
     * <p>Nur für Schaltflächen, die UNMITTELBAR handeln. Ein Knopf, der bloß eine Rückfrage
     * öffnet, braucht ihn nicht - dort schützt schon der modale Dialog, und nach einem "Nein"
     * wäre der Knopf sonst tot (siehe {@code AdminOfflineIncidentsView#actionButtons}).
     */
    public static Button onAction(Button button, Runnable action) {
        button.setDisableOnClick(true);
        button.addClickListener(e -> {
            try {
                action.run();
            } finally {
                button.setEnabled(true);
            }
        });
        return button;
    }
}
