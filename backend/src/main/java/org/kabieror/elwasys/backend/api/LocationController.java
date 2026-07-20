package org.kabieror.elwasys.backend.api;

import org.kabieror.elwasys.backend.api.dto.LocationDto;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standort-Selbstauskunft für ein Terminal: welcher Standort gehört zum vorgelegten Token
 * (AP4). Nützlich, damit ein Terminal seine eigene Standort-Bezeichnung anzeigen kann, ohne
 * sie lokal zu konfigurieren.
 */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    @GetMapping("/me")
    public LocationDto me(@AuthenticationPrincipal TerminalPrincipal terminal) {
        return new LocationDto(terminal.locationId(), terminal.locationName());
    }
}
