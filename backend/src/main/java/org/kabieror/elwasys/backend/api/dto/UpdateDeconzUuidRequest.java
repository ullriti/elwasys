package org.kabieror.elwasys.backend.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body für {@code POST /api/v1/devices/{id}/deconz-uuid} (Phase 4 AP4, additiv). Entspricht
 * dem Teil von {@code Device#modify(...)}, den {@code DeconzRegistrationService#registerDevice}
 * im Client-Alt-Code nach einer erfolgreichen Pairing-Suche aufruft: die neu gefundene
 * deCONZ-Geräte-Id wird auf dem Gerät hinterlegt, alle anderen Felder bleiben unverändert. Bis
 * AP4 gab es dafür KEINEN API-Endpunkt - die AP3-Inventur hatte diesen (untesteten,
 * Admin-Registrierungs-)Pfad übersehen (siehe docs/kb/05-migration-plan.md, Änderungslog
 * "Phase 4 AP4").
 *
 * <p><b>Validierung (Issue #42, Pre-Launch AP4):</b> die deCONZ-Id ist erforderlich und
 * längenbegrenzt – ein leerer oder überlanger Wert wird an der API-Grenze mit
 * {@code 400 Bad Request} abgewiesen, statt ein Gerät mit einer unbrauchbaren Kennung zu
 * überschreiben.
 */
public record UpdateDeconzUuidRequest(@NotBlank @Size(max = 64) String deconzUuid) {
}
