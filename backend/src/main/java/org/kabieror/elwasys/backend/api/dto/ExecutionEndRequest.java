package org.kabieror.elwasys.backend.api.dto;

import java.time.LocalDateTime;

/**
 * Optionaler Anfrage-Rumpf für {@code POST /api/v1/executions/{id}/finish} und {@code
 * .../abort} (AP3, Phase 4, additiv). Beide Endpunkte kamen aus AP4 ohne Anfrage-Rumpf (reine
 * {@code POST}s ohne Body) - {@code clientTimestamp} ergänzt das um denselben
 * Original-Zeitstempel-Gedanken wie {@link ExecutionStartRequest#clientTimestamp()} (fehlt er,
 * verwendet der Server weiterhin {@code LocalDateTime.now()}). Der Anfrage-Rumpf selbst bleibt
 * optional ({@code @RequestBody(required = false)} im Controller) - ein Aufruf ganz ohne Body,
 * wie ihn die bestehenden Tests/Clients senden, funktioniert unverändert.
 */
public record ExecutionEndRequest(LocalDateTime clientTimestamp) {
}
