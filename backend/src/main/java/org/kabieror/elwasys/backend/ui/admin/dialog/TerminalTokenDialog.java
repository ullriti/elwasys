package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;
import org.kabieror.elwasys.backend.ui.component.AbstractFormDialog;
import org.kabieror.elwasys.backend.ui.component.ConfirmDeleteDialog;
import org.kabieror.elwasys.backend.ui.component.PortalFormats;

/**
 * Verwaltung der Standort-Tokens eines Standorts ("Terminal-Tokens"): auflisten, neu erzeugen,
 * widerrufen. Erreichbar über die Zeilenaktion in der Standortverwaltung
 * ({@code AdminLocationsView}), deshalb - wie {@code CreditHistoryDialog}/
 * {@code ExpiredExecutionsDialog} - ein Dialog statt einer eigenen Route.
 *
 * <p>Ein Standort-Token authentifiziert ein Raspi-Terminal gegenüber der REST-API und dem
 * WebSocket-Endpunkt (siehe {@link TerminalTokenService}). Bis hierher war der CLI-Runner
 * ({@code TerminalTokenCliRunner}, Profil {@code token-cli}) der einzige Weg dorthin; der
 * Auftraggeber hat die damalige Festlegung "kein Admin-UI" revidiert, weil ein neues Terminal
 * sonst nur mit Server-Zugriff in Betrieb genommen werden kann. Der CLI-Runner bleibt als
 * Ops-Weg für den Cutover bestehen.
 *
 * <p><b>Das Klartext-Token</b> existiert ausschließlich im Rückgabewert von
 * {@link TerminalTokenService#createToken} - in der Datenbank steht nur sein Hash. Es wird
 * deshalb genau einmal angezeigt (Hinweisfeld über der Liste, verschwindet mit dem Dialog) und
 * bewusst NIRGENDS protokolliert: kein Log-Eintrag, keine Notification, kein Grid-Inhalt. Auch
 * der gespeicherte Hash wird nicht angezeigt - er ist für den Bediener wertlos und wäre nur ein
 * unnötig ausgestelltes Geheimnis.
 *
 * <p><b>Zugriff</b>: der Dialog trägt selbst keine Rollenprüfung - er ist keine Route, sondern
 * wird ausschließlich aus {@code AdminLocationsView} geöffnet, die über
 * {@code @RolesAllowed("ADMIN")} abgesichert ist (Vaadins {@code NavigationAccessControl} prüft
 * pro Route, siehe {@code AdminLayout}-Javadoc). Ein Nicht-Admin kommt damit gar nicht erst auf
 * die Seite, aus der dieser Dialog erreichbar ist.
 */
public class TerminalTokenDialog extends Dialog {

    private final TerminalTokenService tokenService;
    private final LocationEntity location;

    private final Grid<TerminalTokenEntity> grid = new Grid<>();

    private final TextField tfLabel = new TextField("Beschriftung (optional)");

    /**
     * Die einmalige Anzeige des zuletzt erzeugten Klartext-Tokens. Anfangs unsichtbar; sie wird
     * beim Erzeugen befüllt und lebt nur so lange wie dieser Dialog (der Dialog wird je Klick
     * neu instanziiert, siehe {@code AdminLocationsView#openTokenDialog}).
     */
    private final Div issuedTokenBox = new Div();

    private final TextField tfIssuedToken = new TextField("Neues Token");

    public TerminalTokenDialog(TerminalTokenService tokenService, LocationEntity location) {
        this.tokenService = tokenService;
        this.location = location;

        setHeaderTitle("Terminal-Tokens von " + location.getName());
        setModal(true);
        setResizable(true);
        setWidth("60em");
        // UI-Redesign v2: dasselbe Schließen-Kreuz wie in den Formular-Dialogen (dieser Dialog
        // erbt bewusst nicht von AbstractFormDialog, siehe dortiges Klassen-Javadoc).
        AbstractFormDialog.addCloseButton(this);

        add(buildExplanation(), buildCreateRow(), buildIssuedTokenBox(), buildGrid());

        // Das Klartext-Token soll den geschlossenen Dialog nicht überleben - auch nicht
        // serverseitig im Feldwert. Vaadin hält eine geschlossene Dialog-Instanz bis zum
        // Verlassen der Ansicht am UI-Baum; ohne dieses Aufräumen läge das Credential dort
        // weiter herum, obwohl es niemand mehr sehen kann.
        addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                this.tfIssuedToken.clear();
                this.issuedTokenBox.setVisible(false);
            }
        });

        loadData();
    }

    /** Erklärt, wofür ein Token gilt und warum ein Widerruf nichts löscht. */
    private static Paragraph buildExplanation() {
        Paragraph explanation = new Paragraph(
                "Mit einem Standort-Token meldet sich ein Terminal dieses Standorts am Backend an. Pro Standort "
                        + "dürfen mehrere Tokens gleichzeitig gültig sein - so lässt sich ein Terminal auf ein neues "
                        + "Token umstellen, bevor das alte widerrufen wird (Rotation ohne Ausfall). Ein widerrufenes "
                        + "Token wird nicht gelöscht, sondern bleibt als Nachweis in der Liste.");
        explanation.addClassName("small");
        return explanation;
    }

    private HorizontalLayout buildCreateRow() {
        this.tfLabel.setPlaceholder("z. B. Terminal Waschküche");
        this.tfLabel.setHelperText("Nur zur Wiedererkennung in dieser Liste.");
        this.tfLabel.setWidthFull();

        Button btnCreate = new Button("Token erzeugen");
        btnCreate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        // Doppelklick-Schutz wie auf den direkt handelnden Knöpfen des Portals (Issue #49):
        // ohne ihn entstünden aus einem Doppelklick zwei gültige Terminal-Credentials, von denen
        // der Bediener nur das zuletzt angezeigte kennt.
        btnCreate.setDisableOnClick(true);
        btnCreate.addClickListener(e -> {
            try {
                createToken();
            } finally {
                // MUSS von Hand zurückgesetzt werden: setDisableOnClick hängt lediglich einen
                // eigenen Klick-Listener ein, der den Knopf serverseitig auf disabled setzt
                // (Vaadins DisableOnClickController) - es reaktiviert ihn NICHT nach dem
                // Roundtrip. Ohne dieses Zurücksetzen ließe sich je Dialog nur ein einziges
                // Token erzeugen, danach wäre der Knopf bis zum Schließen tot (nachgewiesen in
                // terminal-tokens.spec.ts, P33 legt zwei Tokens hintereinander an).
                btnCreate.setEnabled(true);
            }
        });

        HorizontalLayout row = new HorizontalLayout(this.tfLabel, btnCreate);
        row.setWidthFull();
        row.setFlexGrow(1, this.tfLabel);
        // Der Knopf sitzt auf der Grundlinie des Eingabefelds, nicht über dessen Label.
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        return row;
    }

    private Div buildIssuedTokenBox() {
        Span warning = new Span("Dieses Token wird nur EINMAL angezeigt und nirgends gespeichert - jetzt sicher in "
                + "die Terminal-Konfiguration übernehmen. Nach dem Schließen dieses Dialogs lässt es sich nicht "
                + "erneut anzeigen.");
        warning.addClassName("token-reveal-warning");

        // Nur-Lesen statt eines reinen Textknotens: der Wert bleibt markierbar/kopierbar, kann
        // aber nicht versehentlich verändert werden. Bewusst kein Kopier-Knopf - das Portal
        // führt an keiner Stelle clientseitiges JavaScript aus, und ein einzelner
        // Clipboard-Aufruf wäre der Anfang davon.
        this.tfIssuedToken.setReadOnly(true);
        this.tfIssuedToken.setWidthFull();
        this.tfIssuedToken.addClassName("token-reveal-value");

        this.issuedTokenBox.addClassName("token-reveal");
        this.issuedTokenBox.add(warning, this.tfIssuedToken);
        this.issuedTokenBox.setVisible(false);
        return this.issuedTokenBox;
    }

    private Grid<TerminalTokenEntity> buildGrid() {
        this.grid.setHeight("20em");
        this.grid.setWidthFull();

        // Bewusst OHNE token_hash: der Hash ist für den Bediener wertlos und gehört als
        // gespeichertes Geheimnis nicht auf den Bildschirm (siehe Klassen-Javadoc).
        this.grid.addColumn(TerminalTokenEntity::getId).setHeader("Id").setFlexGrow(0).setAutoWidth(true);
        this.grid.addColumn(TerminalTokenDialog::labelText).setHeader("Beschriftung");
        this.grid.addColumn(t -> PortalFormats.dateTime(t.getCreatedAt())).setHeader("Erstellt").setFlexGrow(0)
                .setAutoWidth(true);
        // "-", solange sich noch kein Terminal mit diesem Token gemeldet hat (PortalFormats
        // bildet null so ab) - der Bediener erkennt daran ein nie benutztes Token.
        this.grid.addColumn(t -> PortalFormats.dateTime(t.getLastUsedAt())).setHeader("Zuletzt benutzt")
                .setFlexGrow(0).setAutoWidth(true);
        this.grid.addComponentColumn(TerminalTokenDialog::statusBadge).setHeader("Status").setFlexGrow(0)
                .setAutoWidth(true);
        this.grid.addComponentColumn(this::rowButtons).setHeader("").setFlexGrow(0).setAutoWidth(true);
        return this.grid;
    }

    /** Ein Token ohne Beschriftung bleibt zulässig (das Feld ist optional) - dann "-". */
    private static String labelText(TerminalTokenEntity token) {
        return token.getLabel() == null || token.getLabel().isBlank() ? "-" : token.getLabel();
    }

    /**
     * Beschriftung der Status-Spalte, inklusive Widerrufs-Zeitpunkt. Eigene Methode, damit der
     * Badge-Text nur eine Quelle hat - wie {@code statusLabel} in den Listenansichten.
     */
    private static String statusLabel(TerminalTokenEntity token) {
        return token.isActive() ? "Aktiv" : "Widerrufen am " + PortalFormats.dateTime(token.getRevokedAt());
    }

    private static Span statusBadge(TerminalTokenEntity token) {
        Span badge = new Span(statusLabel(token));
        badge.getElement().getThemeList().add("badge" + (token.isActive() ? " success" : " error"));
        return badge;
    }

    private Component rowButtons(TerminalTokenEntity token) {
        if (!token.isActive()) {
            // Ein widerrufenes Token ist nur noch Nachweis - es gibt keine Aktion mehr darauf.
            return new Span();
        }
        Button btnRevoke = new Button("Widerrufen", e -> confirmRevoke(token));
        btnRevoke.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        // BEWUSST OHNE setDisableOnClick (wie der "Quittieren"-Knopf der Offline-Vorfälle):
        // dieser Knopf handelt nicht selbst, er öffnet nur die Rückfrage. Nach einem "Nein" wäre
        // er sonst bis zum Neuladen tot.
        return btnRevoke;
    }

    /**
     * Der Widerruf sperrt ein Terminal aus und ist nicht zurücknehmbar - deshalb dieselbe
     * ausdrückliche Ja/Nein-Rückfrage wie die Löschpfade des Portals.
     */
    private void confirmRevoke(TerminalTokenEntity token) {
        ConfirmDeleteDialog.show("Token widerrufen",
                "Möchten Sie dieses Token wirklich widerrufen? Ein Terminal, das noch mit diesem Token arbeitet, "
                        + "verliert sofort den Zugang zum Backend. Der Widerruf lässt sich nicht rückgängig machen.",
                () -> revoke(token));
    }

    private void revoke(TerminalTokenEntity token) {
        this.tokenService.revoke(token.getId());
        loadData();
    }

    private void createToken() {
        String label = this.tfLabel.getValue() == null || this.tfLabel.getValue().isBlank() ? null
                : this.tfLabel.getValue().trim();

        IssuedTerminalToken issued = this.tokenService.createToken(this.location, label);

        // Das Klartext-Token geht ausschließlich in dieses Feld - nicht ins Log und nicht in
        // eine Notification (siehe Klassen-Javadoc).
        this.tfIssuedToken.setValue(issued.rawToken());
        this.issuedTokenBox.setVisible(true);
        this.tfLabel.clear();
        loadData();
    }

    private void loadData() {
        this.grid.setItems(this.tokenService.findByLocation(this.location));
        // autoWidth misst erst, wenn Zellinhalt gerendert ist - der kommt aber gerade eben erst.
        this.grid.recalculateColumnWidths();
    }
}
