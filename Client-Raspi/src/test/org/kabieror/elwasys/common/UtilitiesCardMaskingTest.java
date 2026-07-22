package org.kabieror.elwasys.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest zu Issue #56: RFID-Karten-Ids dürfen nicht im Klartext in ein per
 * Fernwartung ({@code LOG_REQUEST}) abrufbares Log gelangen. {@link Utilities#maskCardId(String)}
 * lässt höchstens die letzten vier Zeichen sichtbar.
 */
class UtilitiesCardMaskingTest {

    @Test
    void masks_all_but_the_last_four_digits() {
        String masked = Utilities.maskCardId("0123456789");
        assertEquals("******6789", masked);
        assertEquals(10, masked.length(), "Die Länge soll erhalten bleiben");
        assertFalse(masked.contains("012345"), "Der vordere Teil der Id darf nicht im Klartext auftauchen");
        assertTrue(masked.endsWith("6789"));
    }

    @Test
    void short_ids_are_fully_masked() {
        // Ids mit höchstens vier Zeichen werden vollständig maskiert - sonst wäre die ganze Id sichtbar.
        assertEquals("****", Utilities.maskCardId("1234"));
        assertEquals("**", Utilities.maskCardId("12"));
        assertEquals("", Utilities.maskCardId(""));
    }

    @Test
    void null_stays_null() {
        assertNull(Utilities.maskCardId(null));
    }

    /**
     * Kern-Sicherheitszusicherung, auf die sich ALLE Log-Aufrufstellen stützen (CardReader-DEBUG
     * sowie die "no user associated to card"-WARN-Meldungen in small/medium MainFormController):
     * die vollständige Karten-Id darf niemals als Teilstring im maskierten Wert erscheinen.
     */
    @Test
    void the_full_card_id_never_appears_in_the_masked_value() {
        for (String cardId : new String[] {"6392847163", "12345", "00000000000000", "9999"}) {
            String masked = Utilities.maskCardId(cardId);
            assertFalse(masked.contains(cardId),
                    "Die vollständige Karten-Id '" + cardId + "' darf nicht im maskierten Log-Wert stehen");
        }
    }
}
