package org.kabieror.elwasys.raspiclient.ui;

/**
 * Definiert mögliche Zustände des Haupfensters
 *
 * @author Oliver Kabierschke
 */
public enum MainFormState {
    /**
     * Nicht definierter Zustand.
     */
    ANY,

    /**
     * Initialer Zustand
     */
    INIT,

    /**
     * Systemstart
     */
    STARTUP,

    /**
     * Auswahlseite für Geräte
     */
    SELECT_DEVICE,

    /**
     * Info zum Gerät
     */
    DEVICE_INFO,

    /**
     * Abbruch einer Programmausführung bestätigen
     */
    CONFIRM_PROGRAM_ABORTION,

    /**
     * Auswahlseite für Programme (nachdem ein Gerät gewählt wurde)
     */
    SELECT_PROGRAM,

    /**
     * Ein Programm wurde gewählt. Warte auf Bestätigung
     */
    PROGRAM_SELECTED,

    /**
     * Allgemeine Bestätigungsseite
     */
    CONFIRMATION,

    /**
     * Bestätigungsseite, warte auf Karte
     */
    CONFIRMATION_WAIT_FOR_CARD,

    /**
     * Bestätigungsseite, Karte unbekannt
     */
    CONFIRMATION_CARD_UNKNOWN,

    /**
     * Bestätigungsseite, Karte erkannt, Beutzer hat zu wenig Guthaben
     */
    CONFIRMATION_CREDIT_INSUFFICIENT,

    /**
     * Bestätigungsseite, Karte erkannt, Benutzer ist gesperrt
     */
    CONFIRMATION_USER_BLOCKED,

    /**
     * Bestätigungsseite, Karte erkannt
     */
    CONFIRMATION_READY,

    /**
     * Programm zum Tür öffnen ist aktiv
     */
    OPEN_DOOR,

    /**
     * Benutzereinstellungen werden angezeigt
     */
    USER_SETTINGS,

    /**
     * Guthabenkonto des Benutzers wird angezeigt
     */
    USER_CREDIT,

    /**
     * Fehlerzustand
     */
    ERROR,

    /**
     * Fehlerzustand, mit der Option zum Wiederholen der fehlgeschlagenen Aktion
     */
    ERROR_RETRYABLE,
}
