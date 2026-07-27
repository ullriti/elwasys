package org.kabieror.elwasys.backend.api;

import org.kabieror.elwasys.backend.api.dto.SnapshotDto;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standort-Snapshot für die Offline-Buchungs-Vorbereitung (AP3, Phase 4, siehe
 * {@link SnapshotDto} Javadoc und docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen
 * am Terminal", Punkt 1 "Lokaler Daten-Snapshot"). Standort-Scope kommt implizit aus dem
 * Terminal-Token (kein Pfad-/Query-Parameter nötig, analog {@code LocationController#me}).
 *
 * <p>Die eigentliche Zusammenstellung liegt in {@link SnapshotService} (Issue #90) - der
 * Controller reicht nur die Standort-Id des authentifizierten Terminals durch.
 */
@RestController
@RequestMapping("/api/v1/snapshot")
public class SnapshotController {

    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping
    public SnapshotDto snapshot(@AuthenticationPrincipal TerminalPrincipal terminal) {
        // Der Standort ist per Konstruktion vorhanden (er stammt aus dem authentifizierten
        // Terminal-Token, siehe TerminalTokenService#createToken), daher ohne weitere
        // Fehlerbehandlung nachgeladen - analog CardLoginController.
        return this.snapshotService.buildSnapshot(terminal.locationId());
    }
}
