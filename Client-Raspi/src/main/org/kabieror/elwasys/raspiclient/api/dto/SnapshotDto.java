package org.kabieror.elwasys.raspiclient.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gegenstück zu {@code backend.api.dto.SnapshotDto} (Phase 4 AP6, siehe
 * kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal"). Wird periodisch
 * über {@code ApiClient#getSnapshot()} geladen und von {@code offline.OfflineSnapshotStore}
 * persistiert (Grundlage für Kartenlogin/Berechtigungs-/Guthabenprüfung, wenn das Backend
 * nicht erreichbar ist - siehe {@code offline.OfflineGateway}).
 *
 * @param offlineMaxDurationMinutes die vom Standort konfigurierte {@code offline.max-duration}
 *                                  (Portal-Standorte-Dialog) - {@link #generatedAt()} plus
 *                                  diese Anzahl Minuten ist der Zeitpunkt, ab dem dieser
 *                                  Snapshot als "zu alt" gilt und keine neuen Offline-Buchungen
 *                                  mehr akzeptiert werden (Fehlerbild wie C15).
 */
public record SnapshotDto(Integer locationId, String locationName, LocalDateTime generatedAt,
        int offlineMaxDurationMinutes, List<SnapshotUserGroupDto> userGroups, List<SnapshotUserDto> users,
        List<SnapshotDeviceDto> devices, List<SnapshotProgramDto> programs) {
}
