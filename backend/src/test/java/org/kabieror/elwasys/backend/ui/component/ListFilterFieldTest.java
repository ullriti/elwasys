package org.kabieror.elwasys.backend.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Die Suchsemantik des Portal-Freitextfilters (UI-Redesign v2, siehe
 * docs/specs/0002-ui-design/v2/MAPPING.md, Abschnitt "Listen"). Reiner Unit-Test ohne Browser -
 * {@link ListFilterField#matches} ist genau dafür als reine Funktion herausgezogen (dasselbe
 * Muster wie {@code Notifications#failureText}), und derselbe Vergleich läuft in
 * {@code reapply()} auf dem {@code ListDataProvider} des Grids.
 *
 * <p>Warum überhaupt geprüft: das Feld steht über drei Listen (Admin-Listen, Offline-Vorfälle,
 * Buchungen im Benutzerportal) und ist der einzige Weg, eine lange Liste einzugrenzen. Findet es
 * einen sichtbaren Text nicht, sieht das für den Benutzer aus wie "den Datensatz gibt es nicht" -
 * der Schwerpunkt liegt deshalb auf den Fällen, in denen Getipptes und Angezeigtes gerade NICHT
 * zeichenweise gleich sind.
 *
 * <p>Die geschützten Leerzeichen stehen bewusst als {@code \}{@code u00A0}/{@code \}{@code u202F}
 * im Quelltext: als rohe Zeichen wären sie von einem normalen Leerzeichen nicht zu unterscheiden,
 * und der Test prüft genau diesen Unterschied.
 */
class ListFilterFieldTest {

    /**
     * Der Fall, der die Normalisierung überhaupt motiviert hat: {@link PortalFormats#currency}
     * setzt (über {@code NumberFormat} in deutscher Schreibweise) ein GESCHÜTZTES Leerzeichen vor
     * das Währungszeichen. Dieses Zeichen liegt auf keiner Tastatur - wer den Betrag abtippt, den
     * er auf dem Bildschirm liest, tippt zwangsläufig ein normales Leerzeichen.
     */
    @Test
    void anAmountTypedWithAnOrdinarySpaceFindsTheNonBreakingSpaceOfTheDisplayedAmount() {
        String displayed = PortalFormats.currency(new BigDecimal("1.50"));
        assertThat(displayed)
                .as("Vorbedingung: der angezeigte Betrag trägt ein geschütztes Leerzeichen")
                .contains("\u00A0").doesNotContain(" ");

        assertThat(ListFilterField.matches(displayed, ListFilterField.normalize("1,50 €"))).isTrue();
    }

    @Test
    void searchIsCaseInsensitive() {
        assertThat(ListFilterField.matches("E2E Portal User", ListFilterField.normalize("PORTAL"))).isTrue();
        assertThat(ListFilterField.matches("E2E PORTAL USER", ListFilterField.normalize("portal"))).isTrue();
    }

    @Test
    void umlautsAreFoundRegardlessOfCase() {
        // Kleinschreibung nach deutschem Gebietsschema: "Ä" und "ä" müssen sich treffen, sonst
        // fände der Filter die halbe deutschsprachige Oberfläche nicht ("Geräte", "Umsätze", ...).
        assertThat(ListFilterField.matches("Waschküche Süd", ListFilterField.normalize("küche"))).isTrue();
        assertThat(ListFilterField.matches("WASCHKÜCHE SÜD", ListFilterField.normalize("küche"))).isTrue();
        assertThat(ListFilterField.matches("Waschküche Süd", ListFilterField.normalize("KÜCHE"))).isTrue();
    }

    @Test
    void aTermThatIsNowhereInTheRowDoesNotMatch() {
        assertThat(ListFilterField.matches("Waschküche Süd", ListFilterField.normalize("Nord"))).isFalse();
    }

    @Test
    void aRowWithoutSearchableTextNeverMatches() {
        // Nicht jede Zeile hat in jeder Spalte einen Wert (z.B. ein Offline-Vorfall ohne bekannten
        // Benutzer) - ein fehlender Suchtext darf weder eine NullPointerException auslösen noch
        // versehentlich als Treffer durchgehen.
        assertThat(ListFilterField.matches(null, ListFilterField.normalize("Nord"))).isFalse();
    }

    @Test
    void anEmptyOrBlankTermClearsTheFilterInsteadOfMatchingNothing() {
        // reapply() entscheidet am NORMALISIERTEN Begriff, ob es filtert oder den Filter löscht:
        // reiner Leerraum (auch geschützter) muss dort als "leer" ankommen, sonst bliebe die
        // Liste nach dem Leeren des Feldes gefiltert.
        assertThat(ListFilterField.normalize(null)).isEmpty();
        assertThat(ListFilterField.normalize("   ")).isEmpty();
        assertThat(ListFilterField.normalize("\u00A0\u202F")).isEmpty();

        // Und ein leerer Begriff trifft jede Zeile - der Zustand "kein Filter".
        assertThat(ListFilterField.matches("Waschküche Süd", "")).isTrue();
    }

    @Test
    void leadingAndTrailingWhitespaceOfTheTermIsIgnored() {
        assertThat(ListFilterField.normalize("  Süd  ")).isEqualTo("süd");
        assertThat(ListFilterField.matches("Waschküche Süd", ListFilterField.normalize("  Süd  "))).isTrue();
    }
}
