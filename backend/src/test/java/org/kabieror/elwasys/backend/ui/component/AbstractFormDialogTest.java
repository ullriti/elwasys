package org.kabieror.elwasys.backend.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Die Fehlermeldung eines fehlgeschlagenen Dialog-Speicherns (Issue #92). Das ist die einzige
 * echte Verhaltensänderung des Dialog-Gerüsts und darum die einzige, die einen eigenen Test
 * braucht: die Dialoge hängten bisher unbesehen {@code e.getMessage()} an - bei einer
 * {@code NullPointerException} (typischerweise ohne Meldung) las der Administrator "... null"
 * und der Auslöser stand nirgends.
 */
class AbstractFormDialogTest {

    @Test
    void detailOfTheExceptionIsAppendedWhenThereIsOne() {
        assertEquals("Speichern fehlgeschlagen. Constraint verletzt",
                AbstractFormDialog.failureText("Speichern fehlgeschlagen.", "Constraint verletzt"));
    }

    @Test
    void missingDetailFallsBackToAPointerAtTheServerLog() {
        String expected = "Speichern fehlgeschlagen. Unerwarteter Fehler - Details stehen im Server-Log.";
        assertEquals(expected, AbstractFormDialog.failureText("Speichern fehlgeschlagen.", null),
                "Eine Ausnahme ohne Meldung (z. B. NPE) darf nicht als \"... null\" im Portal landen");
        assertEquals(expected, AbstractFormDialog.failureText("Speichern fehlgeschlagen.", "   "),
                "Auch eine leere Meldung ist für den Administrator wertlos");
    }
}
