package org.kabieror.elwasys.raspiclient.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gegenstück zu {@code backend.api.dto.SnapshotUserDto} (Phase 4 AP6, siehe
 * docs/kb/05-migration-plan.md). Enthält bewusst KEIN Passwort-/Sicherheitsfeld - der Snapshot
 * dient ausschließlich der Karten-Login-/Berechtigungs-/Guthabenprüfung im Offline-Fall
 * (siehe {@code offline.OfflineGateway}).
 */
public record SnapshotUserDto(Integer id, String name, List<String> cardIds, boolean blocked, Integer groupId,
        BigDecimal credit) {
}
