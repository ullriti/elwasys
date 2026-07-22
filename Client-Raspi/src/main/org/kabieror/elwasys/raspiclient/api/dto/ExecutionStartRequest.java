package org.kabieror.elwasys.raspiclient.api.dto;

import java.time.LocalDateTime;

/**
 * Gegenstück zu {@code backend.api.dto.ExecutionStartRequest} (Phase 4 AP4). {@code
 * clientTimestamp} wird beim Anlegen einer Ausführung mitgeschickt (Phase 4 AP3, Original-
 * Zeitstempel für die spätere Offline-Nachmeldung, AP6).
 *
 * <p>{@code replay} (Issue #16): {@code true} kennzeichnet eine Offline-Nachmeldung
 * (privilegierter Nachbuchungs-Pfad) - das Backend überspringt dann die fachlichen Wächter.
 * Für Live-Buchungen {@code false}.
 */
public record ExecutionStartRequest(Integer userId, Integer deviceId, Integer programId,
        LocalDateTime clientTimestamp, Boolean replay) {
}
