package org.kabieror.elwasys.backend.api.dto;

/**
 * Body für {@code POST /api/v1/devices/{id}/deconz-uuid} (Phase 4 AP4, additiv). Entspricht
 * dem Teil von {@code Device#modify(...)}, den {@code DeconzRegistrationService#registerDevice}
 * im Client-Alt-Code nach einer erfolgreichen Pairing-Suche aufruft: die neu gefundene
 * deCONZ-Geräte-Id wird auf dem Gerät hinterlegt, alle anderen Felder bleiben unverändert. Bis
 * AP4 gab es dafür KEINEN API-Endpunkt - die AP3-Inventur hatte diesen (untesteten,
 * Admin-Registrierungs-)Pfad übersehen (siehe docs/kb/05-migration-plan.md, Änderungslog
 * "Phase 4 AP4").
 */
public record UpdateDeconzUuidRequest(String deconzUuid) {
}
