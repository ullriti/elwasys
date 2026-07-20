package org.kabieror.elwasys.backend.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Entspricht {@code DataManager#newExecution} + {@code ExecutionManager#startExecution} im
 * Alt-Code (persistenzseitig - die hardwarenahe Ansteuerung der Steckdose bleibt im
 * Terminal, siehe {@link org.kabieror.elwasys.backend.service.ExecutionService}
 * Klassen-Javadoc).
 */
public record ExecutionStartRequest(@NotNull Integer userId, @NotNull Integer deviceId,
        @NotNull Integer programId) {
}
