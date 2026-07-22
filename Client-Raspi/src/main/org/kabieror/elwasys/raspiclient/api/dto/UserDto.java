package org.kabieror.elwasys.raspiclient.api.dto;

import java.math.BigDecimal;

/**
 * Wire-kompatibles Gegenstück zu {@code org.kabieror.elwasys.backend.api.dto.UserDto}
 * (Phase 4 AP4, siehe docs/kb/05-migration-plan.md). Eigene, schlanke Client-DTOs statt einer
 * Abhängigkeit auf das Backend-Modul (das der Client ohnehin nicht referenzieren kann/soll) -
 * siehe docs/kb/03-modules.md, Abschnitt "Client-Cutover" für die Begründung.
 */
public record UserDto(Integer id, String name, String username, String email, boolean admin, boolean blocked,
        Integer groupId, String groupName, BigDecimal credit) {
}
