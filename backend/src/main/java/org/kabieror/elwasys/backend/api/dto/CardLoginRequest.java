package org.kabieror.elwasys.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Entspricht dem RFID-Kartenlogin am Terminal ({@code CardDetectedEvent}/
 * {@code MainFormController#onCardDetected} im Alt-Code).
 *
 * <p><b>Strenge Formatvalidierung (Issue #21, Pre-Launch AP4):</b> das Terminal liest
 * ausschließlich Dezimalziffern als Kartennummer ({@code Client-Raspi/.../io/CardReader});
 * das hier zugelassene hexadezimale Zeicheninventar {@code [0-9A-Fa-f]} ist bewusst eine
 * sichere Obermenge davon (kein Verhaltensbruch für echte Karten). Zusätzlich zur regex-freien
 * Suche ({@code UserRepository#findByCardId}) begrenzt dieses Feld die Eingabe als Verteidigung
 * in der Tiefe auf genau dieses Inventar: ein Injection-/Metazeichen-Muster (z.B. {@code .*})
 * wird damit bereits an der API-Grenze mit {@code 400 Bad Request} abgewiesen, bevor es die
 * Persistenz erreicht.
 */
public record CardLoginRequest(
        @NotBlank @Pattern(regexp = "[0-9A-Fa-f]{1,50}") String cardId) {
}
