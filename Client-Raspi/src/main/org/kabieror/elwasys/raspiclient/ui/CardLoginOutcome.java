package org.kabieror.elwasys.raspiclient.ui;

import org.kabieror.elwasys.common.Utilities;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.slf4j.Logger;

/**
 * Die fachlichen Ausgänge eines fehlgeschlagenen Kartenlogins
 * ({@code POST /api/v1/card-login}, siehe {@code CardLoginController} im Backend).
 * <p>
 * Beide UI-Größen ({@code ui/small} und {@code ui/medium}) haben die Abbildung
 * "Fehler-Slug &rarr; Ursache" samt maskiertem Log zuvor nahezu wortgleich reimplementiert
 * (#91). Hier liegt jetzt der gemeinsame Anteil: die Klassifikation und das Logging. Die
 * eigentliche UI-Reaktion bleibt bewusst je Größe verschieden - {@code ui/medium} markiert die
 * Toolbar, {@code ui/small} wechselt in einen Bestätigungs-Zwischenzustand.
 */
public enum CardLoginOutcome {

    /**
     * Zu der aufgelegten Karte gibt es keinen Benutzer.
     */
    CARD_NOT_FOUND,

    /**
     * Der Benutzer ist gesperrt.
     */
    USER_BLOCKED,

    /**
     * Die Benutzergruppe des Benutzers ist an diesem Standort nicht zugelassen.
     */
    LOCATION_NOT_ALLOWED,

    /**
     * Jeder andere Fehler (insbesondere ein Kommunikationsfehler) - führt in beiden UI-Größen
     * zur allgemeinen Fehleranzeige mit Wiederholen-Option.
     */
    COMMUNICATION_ERROR;

    /**
     * Klassifiziert die vom Backend gemeldete Ausnahme.
     *
     * @param e Die Ausnahme des fehlgeschlagenen Kartenlogins.
     * @return Der zugehörige Ausgang.
     */
    public static CardLoginOutcome of(ApiException e) {
        if (e.is(404, "card-not-found")) {
            return CARD_NOT_FOUND;
        }
        if (e.is(403, "user-blocked")) {
            return USER_BLOCKED;
        }
        if (e.is(403, "location-not-allowed")) {
            return LOCATION_NOT_ALLOWED;
        }
        return COMMUNICATION_ERROR;
    }

    /**
     * Schreibt den zu diesem Ausgang gehörenden Log-Eintrag. Die Karten-Id wird dabei maskiert
     * (Issue #56): sie ist das einzige klonbare Terminal-Login-Merkmal und darf nicht im Klartext
     * in ein per Fernwartung (LOG_REQUEST) abrufbares Log gelangen.
     *
     * @param logger Der Logger des aufrufenden Controllers (damit der Log-Kontext die UI-Größe
     *               erkennen lässt).
     * @param cardId Die aufgelegte Karten-Id.
     * @param e      Die Ausnahme des fehlgeschlagenen Kartenlogins.
     */
    public void log(Logger logger, String cardId, ApiException e) {
        switch (this) {
            case CARD_NOT_FOUND -> logger.warn("There is no user associated to card {}.",
                    Utilities.maskCardId(cardId));
            case USER_BLOCKED -> logger.info("Blocked user tried to log in.");
            case LOCATION_NOT_ALLOWED -> logger.info("User is not allowed to use this location.");
            case COMMUNICATION_ERROR -> logger.error("Communication error while looking up user.", e);
        }
    }
}
