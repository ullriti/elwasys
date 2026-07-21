package org.kabieror.elwasys.raspiclient.api.dto;

/**
 * Gegenstück zu {@code backend.api.dto.UpdateDeconzUuidRequest} (Phase 4 AP4, additiv - siehe
 * {@link org.kabieror.elwasys.raspiclient.devices.deconz.DeconzRegistrationService}).
 */
public record UpdateDeconzUuidRequest(String deconzUuid) {
}
