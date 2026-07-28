package org.kabieror.elwasys.backend.ui.admin.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.internal.nodefeature.ElementPropertyMap;
import com.vaadin.flow.internal.nodefeature.PropertyChangeDeniedException;
import elemental.json.Json;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;

/**
 * Die eine Zusicherung des {@link TerminalTokenDialog}, die sich im Browser nicht belegen lässt:
 * das einmalig angezeigte Klartext-Token verschwindet beim Schließen auch SERVERSEITIG aus dem
 * Feldwert.
 *
 * <p>Die Playwright-Suite (P32) kann das nicht: dort wird der Dialog geschlossen und neu
 * geöffnet - und weil {@code AdminLocationsView#openTokenDialog} je Klick eine frische Instanz
 * baut, ist der zweite Dialog ohnehin leer. Der Test bliebe grün, wenn man das Aufräumen
 * ersatzlos löschte. Deshalb hier serverseitig, ohne Browser (Muster
 * {@code AdminDashboardViewTest}/{@code ListFilterFieldTest}, nur mit einer {@link UI} im
 * Thread-Kontext - {@code Dialog#open} hängt sich über Vaadins {@code OverlayAutoAddController}
 * an die aktuelle UI und scheitert sonst mit einer {@code IllegalStateException}).
 *
 * <p>Beide Schließwege sind abgedeckt: der Knopf/Programmaufruf ({@code close()}) und der
 * clientseitige Weg (ESC, Klick daneben). Letzterer erreicht den Server als
 * <b>benutzerinitiierte</b> Änderung des {@code opened}-Properties - genau daran hängt Vaadin
 * das {@code OpenedChangeEvent}, auf dem das Aufräumen sitzt.
 */
class TerminalTokenDialogTest {

    private static final String RAW_TOKEN = "elwt_TEST-nur-in-diesem-Test";

    private TerminalTokenService tokenService;

    private LocationEntity location;

    /**
     * MUSS als Feld gehalten werden: {@code UI.setCurrent} legt die UI über Vaadins
     * {@code CurrentInstance} nur als schwache Referenz ab. Ohne eine starke Referenz kann die
     * Garbage Collection sie mitten im Test einsammeln, und {@code Dialog#open} scheitert dann
     * mit "UI instance is not available" - sporadisch, je nach GC-Zeitpunkt.
     */
    private UI ui;

    @BeforeEach
    void setUp() {
        this.ui = new UI();
        UI.setCurrent(this.ui);

        this.location = new LocationEntity("Waschküche Süd");
        this.tokenService = mock(TerminalTokenService.class);
        when(this.tokenService.findByLocation(any())).thenReturn(List.of());
        when(this.tokenService.createToken(any(), any())).thenReturn(
                new IssuedTerminalToken(RAW_TOKEN, new TerminalTokenEntity(this.location, "hash", "label")));
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void closingTheDialogClearsTheRevealedPlaintextToken() {
        TerminalTokenDialog dialog = openDialogWithIssuedToken();

        dialog.close();

        assertThat(issuedTokenField(dialog).getValue())
                .as("das Klartext-Token darf den geschlossenen Dialog nicht überleben").isEmpty();
    }

    @Test
    void closingTheDialogFromTheClientClearsTheRevealedPlaintextToken() throws Exception {
        TerminalTokenDialog dialog = openDialogWithIssuedToken();

        // ESC bzw. Klick neben den Dialog: der Browser setzt das opened-Property auf false und
        // meldet das dem Server (Dialog#isOpened trägt dafür @Synchronize("opened-changed")).
        // Genau so kommt der Schließweg ohne Zutun des Servers an - er darf nicht am Aufräumen
        // vorbeilaufen.
        closeFromClient(dialog);

        assertThat(dialog.isOpened()).as("Vorbedingung: der Dialog gilt danach als geschlossen").isFalse();
        assertThat(issuedTokenField(dialog).getValue()).isEmpty();
    }

    /** Öffnet den Dialog und erzeugt ein Token - danach steht der Klartext im Feld. */
    private TerminalTokenDialog openDialogWithIssuedToken() {
        TerminalTokenDialog dialog = new TerminalTokenDialog(this.tokenService, this.location);
        dialog.open();

        DialogComponents.button(dialog, "Token erzeugen").click();

        assertThat(issuedTokenField(dialog).getValue())
                .as("Vorbedingung: das erzeugte Token steht im Anzeigefeld").isEqualTo(RAW_TOKEN);
        return dialog;
    }

    private static void closeFromClient(TerminalTokenDialog dialog) throws PropertyChangeDeniedException {
        ElementPropertyMap.getModel(dialog.getElement().getNode())
                .deferredUpdateFromClient("opened", Json.create(false)).run();
    }

    private static TextField issuedTokenField(TerminalTokenDialog dialog) {
        return DialogComponents.textField(dialog, "Neues Token");
    }
}
