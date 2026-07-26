package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValue;
import java.util.regex.Pattern;

/**
 * Die Feldprüfungen der Formular-Dialoge: ein Feld wird genau dann rot markiert und mit einer
 * Meldung versehen, wenn seine Bedingung verletzt ist - sonst wird die Markierung wieder
 * entfernt.
 *
 * <p>Herausgezogen im Rahmen der finalen Review (R3c, Issue #92): jeder Dialog trug denselben
 * {@code requireText}/{@code requireValue}/{@code requireBigDecimal}-Dreisatz als eigene private
 * Kopie (teils vier Überladungen in einer Klasse, nur wegen des Feldtyps).
 *
 * <p><b>Warum kein Vaadin-{@code Binder}</b> (Empfehlung der Review): Der Binder prüft
 * standardmäßig bei jeder Werteingabe und markiert das Feld schon beim Tippen, während das
 * Portal - wie das Alt-Portal - erst beim Speichern prüft; außerdem schreibt er in eine Bean,
 * die es hier nicht gibt (die Dialoge übergeben ihre Werte als Parameterliste an die Services,
 * mehrere Felder sind vom gewählten Typ/Modus abhängig und ein zusammengesetztes Dauer-Feld ist
 * überhaupt kein {@code HasValue}). Ein Umstieg hätte also entweder das Prüfzeitverhalten
 * geändert oder pro Dialog eine künstliche Formular-Bean gebraucht - beides Preise, die der
 * reine Aufräum-Nutzen nicht rechtfertigt. Details siehe docs/worklog.
 */
public final class FormValidation {

    /**
     * Grob-Prüfung einer Email-Adresse (etwas vor dem @, etwas dahinter, ein Punkt darin, keine
     * Leerzeichen) - bewusst dieselbe nachsichtige Regel wie bisher in den Dialogen und im
     * Alt-Portal, damit keine Adresse abgelehnt wird, die vorher akzeptiert wurde.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private FormValidation() {
        // Utility-Klasse
    }

    /**
     * Pflichtfeld: ein leeres Feld wird als fehlerhaft markiert.
     *
     * @return {@code true}, wenn das Feld gefüllt ist.
     */
    public static <F extends HasValue<?, ?> & HasValidation> boolean require(F field, String message) {
        return check(!field.isEmpty(), field, message);
    }

    /**
     * Markiert das Feld genau dann als fehlerhaft (mit der übergebenen Meldung), wenn
     * {@code valid} nicht zutrifft.
     *
     * @return {@code valid}, damit sich Prüfungen zu einem Gesamtergebnis verketten lassen.
     */
    public static boolean check(boolean valid, HasValidation field, String message) {
        field.setInvalid(!valid);
        if (!valid) {
            field.setErrorMessage(message);
        }
        return valid;
    }

    /**
     * Markiert ein Feld als fehlerhaft, wenn der Fehler bereits feststeht - etwa weil ihn erst
     * der Service gemeldet hat (doppelter Benutzername/doppelte Kartennummer).
     *
     * @return Immer {@code false}, damit sich der Aufruf wie die übrigen Prüfungen verketten
     *         lässt.
     */
    public static boolean reject(HasValidation field, String message) {
        return check(false, field, message);
    }

    /**
     * Ob der Text eine Email-Adresse ist. Die Aufrufer entscheiden selbst, ob eine leere Eingabe
     * erlaubt ist - das unterscheidet sich je Dialog.
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}
