package org.kabieror.elwasys.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Entspricht dem RFID-Kartenlogin am Terminal ({@code CardDetectedEvent}/
 * {@code MainFormController#onCardDetected} im Alt-Code).
 */
public record CardLoginRequest(@NotBlank String cardId) {
}
