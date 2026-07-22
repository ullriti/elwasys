package org.kabieror.elwasys.raspiclient.io;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.kabieror.elwasys.common.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Vector;

/**
 * Dieser Kartenleser wartet auf eine Eingabe vom Kartenleser und benachrichtigt
 * bei einem Ereignis registrierte Listener
 * 
 * @author Oliver Kabierschke
 *
 */
public class CardReader {

    /**
     * Maximale Zeit, die zwischen zwei Tastaturereignissen vergehen darf, bevor
     * die Eingabe zurück gesetzt wird, in Millisekunden
     */
    private static final long MAX_TIME_BETWEEN_CHARACTERS = 1000;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Das Fenster, auf dem auf Tastendrücke gehört werden soll
     */
    private final Stage stage;

    /**
     * Die Listener, die bei Erkennen einer Karte benachrichtigt werden sollen
     */
    private final List<ICardDetectedEventListener> cardDetectedEventListener;

    private LocalDateTime lastKeyEvent;

    private String cachedId = "";

    public CardReader(Stage stage) {
        this.cardDetectedEventListener = new Vector<ICardDetectedEventListener>();
        this.stage = stage;
        this.stage.addEventHandler(KeyEvent.KEY_TYPED, e -> this.onKeyTyped(e));
        this.stage.addEventFilter(KeyEvent.KEY_PRESSED, e -> this.onKeyPressed(e));
    }

    /**
     * Fügt einen Listener zur Liste der zu benachrichtigenden Objekte hinzu
     * 
     * @param l
     *            Der zu benachrichtigende Listener
     */
    public void listenToCardDetectedEvent(ICardDetectedEventListener l) {
        // Idempotent: der CardReader lebt über einen restart() hinweg weiter, während
        // AbstractMainFormController#initiate() sich bei jedem (erneuten) initiate() als Listener
        // anmeldet. Ohne diesen contains-Check löste nach einem Restart jede Karte zwei parallele
        // Login-Threads aus (Issue #27) - analog zum bestehenden Dedupe bei den Ausführungs-Listenern.
        if (!this.cardDetectedEventListener.contains(l)) {
            this.cardDetectedEventListener.add(l);
        }
    }

    /**
     * Entfernt einen Listener aus der Liste der zu benachrichtigenden Objekte
     * 
     * @param o
     *            Der zu entfernende Listener
     */
    public void stopListening(Object o) {
        if (this.cardDetectedEventListener.contains(o)) {
            this.cardDetectedEventListener.remove(o);
        }
    }

    /**
     * Setzt die eingegebene Sequenz an Zahlen zurück, falls die letzte Eingabe
     * zu lange zurück liegt
     */
    private void checkTimeout() {
        if (this.lastKeyEvent != null && Duration.between(this.lastKeyEvent, LocalDateTime.now())
                .toMillis() > CardReader.MAX_TIME_BETWEEN_CHARACTERS) {
            // Zeichenfolge zurücksetzen
            this.cachedId = "";
        }
    }

    /**
     * Wird aufgerufen, sobald eine Taste im Hauptfenster gedrückt wurde
     * 
     * @param e
     *            Das Tastendruckereignis
     */
    private void onKeyTyped(KeyEvent e) {
        this.checkTimeout();

        if (e.getCharacter() != null && e.getCharacter().matches("\\d+")) {
            // Zahl gefunden. An bestehende Id anhängen
            this.cachedId += e.getCharacter();
            this.lastKeyEvent = LocalDateTime.now();
        } else {
            // Kein passendes Zeichen gefunden
            this.cachedId = "";
            return;
        }
    }

    /**
     * Wird aufgerufen, sobald eine Taste im Hauptfenster gedrückt wurde
     * 
     * @param e
     *            Das Tastendruckereignis
     */
    private void onKeyPressed(KeyEvent e) {
        this.checkTimeout();

        this.logger.trace("Key pressed: " + e.getCode().getName());

        if (this.cachedId.length() > 0 && e.getCode().equals(KeyCode.ENTER)) {
            // Ende einer Eingabe
            this.cardDetected(this.cachedId);
            e.consume();
            this.lastKeyEvent = null;
            this.cachedId = "";
            return;
        }
    }

    /**
     * Eine Karte wurde erkannt
     * 
     * @param id
     *            Die Id der Karte
     */
    private void cardDetected(String id) {
        // Karten-Id maskiert loggen: sie ist das einzige, klonbare Login-Merkmal und darf nicht
        // im Klartext in ein per Fernwartung abrufbares Log gelangen (Issue #56).
        this.logger.debug("Card detected: " + Utilities.maskCardId(id));
        for (final ICardDetectedEventListener l : this.cardDetectedEventListener) {
            l.onCardDetected(new CardDetectedEvent(id));
        }
    }
}
