package org.kabieror.elwasys.backend.api;

import org.kabieror.elwasys.backend.api.exception.DeviceNotFoundException;
import org.kabieror.elwasys.backend.api.exception.ExecutionNotFoundException;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.springframework.stereotype.Component;

/**
 * Setzt den Standort-Scope eines Terminal-Tokens für Geräte/Ausführungen durch (AP4, siehe
 * kb/05-migration-plan.md: "ein Token sieht nur Geräte/Executions seines Standorts" - vgl.
 * Client-E2E-Fall C16). Ein Gerät/eine Ausführung eines ANDEREN Standorts wird wie ein
 * unbekanntes behandelt ({@code 404}, nicht {@code 403}) - das verrät keine Existenz an
 * fremden Standorten.
 */
@Component
public class TerminalScopeGuard {

    private final DeviceRepository deviceRepository;

    private final ExecutionRepository executionRepository;

    public TerminalScopeGuard(DeviceRepository deviceRepository, ExecutionRepository executionRepository) {
        this.deviceRepository = deviceRepository;
        this.executionRepository = executionRepository;
    }

    public DeviceEntity requireDeviceInScope(Integer deviceId, TerminalPrincipal principal) {
        DeviceEntity device = this.deviceRepository.findById(deviceId).orElseThrow(
                () -> new DeviceNotFoundException(deviceId));
        if (!device.getLocation().getId().equals(principal.locationId())) {
            throw new DeviceNotFoundException(deviceId);
        }
        return device;
    }

    public ExecutionEntity requireExecutionInScope(Integer executionId, TerminalPrincipal principal) {
        ExecutionEntity execution = this.executionRepository.findById(executionId).orElseThrow(
                () -> new ExecutionNotFoundException(executionId));
        if (!execution.getDevice().getLocation().getId().equals(principal.locationId())) {
            throw new ExecutionNotFoundException(executionId);
        }
        return execution;
    }
}
