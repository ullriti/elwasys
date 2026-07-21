package org.kabieror.elwasys.backend.api.dto;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.kabieror.elwasys.backend.domain.UserEntity;

/**
 * Teil des {@link SnapshotDto} (AP3, Phase 4, siehe Konzeptskizze Punkt 1 "Lokaler
 * Daten-Snapshot": "Nutzer mit Kartennummern, Guthaben, Sperr-Status"). Enthält bewusst
 * <b>KEIN</b> Passwort-/Sicherheitsfeld - der Snapshot dient ausschließlich der
 * Karten-Login-/Berechtigungs-/Guthabenprüfung, niemals einer Passwort-basierten
 * Authentifizierung (die gibt es am Terminal ohnehin nicht, siehe {@code CardLoginController}).
 */
public record SnapshotUserDto(Integer id, String name, List<String> cardIds, boolean blocked, Integer groupId,
        BigDecimal credit) {

    public static SnapshotUserDto of(UserEntity user, BigDecimal credit) {
        // UserEntity#getCardIds() liefert bei leerem Rohwert [""] (String#split-Eigenheit) -
        // fuer den Snapshot werden leere Eintraege bewusst herausgefiltert statt sie 1:1 zu
        // uebernehmen (kein Alt-Code-Verhalten zu bewahren, dies ist ein neues DTO).
        List<String> cardIds = Arrays.stream(user.getCardIds()).filter(id -> !id.isBlank()).toList();
        return new SnapshotUserDto(user.getId(), user.getName(), cardIds, user.isBlocked(), user.getGroup().getId(),
                credit);
    }
}
