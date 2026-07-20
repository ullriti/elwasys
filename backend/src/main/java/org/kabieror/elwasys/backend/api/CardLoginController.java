package org.kabieror.elwasys.backend.api;

import jakarta.validation.Valid;
import org.kabieror.elwasys.backend.api.dto.CardLoginRequest;
import org.kabieror.elwasys.backend.api.dto.UserDto;
import org.kabieror.elwasys.backend.api.exception.CardNotFoundException;
import org.kabieror.elwasys.backend.api.exception.LocationNotAllowedException;
import org.kabieror.elwasys.backend.api.exception.UserBlockedException;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kartenlogin (AP4, siehe kb/05-migration-plan.md) - fachlicher Nachfolger von
 * {@code MainFormController#onCardDetected} im Client-Alt-Code: Kartennummer -&gt;
 * Benutzerdaten inkl. Guthaben, gesperrte Nutzer und standortfremde Benutzergruppen werden
 * wie im Terminal-Altcode abgewiesen.
 */
@RestController
@RequestMapping("/api/v1")
public class CardLoginController {

    private final UserRepository userRepository;

    private final LocationRepository locationRepository;

    private final PermissionService permissionService;

    private final CreditService creditService;

    public CardLoginController(UserRepository userRepository, LocationRepository locationRepository,
            PermissionService permissionService, CreditService creditService) {
        this.userRepository = userRepository;
        this.locationRepository = locationRepository;
        this.permissionService = permissionService;
        this.creditService = creditService;
    }

    @PostMapping("/card-login")
    public UserDto cardLogin(@AuthenticationPrincipal TerminalPrincipal terminal,
            @Valid @RequestBody CardLoginRequest request) {
        UserEntity user = this.userRepository.findByCardId(request.cardId()).orElseThrow(
                () -> new CardNotFoundException(request.cardId()));

        if (user.isBlocked()) {
            throw new UserBlockedException(user.getId());
        }

        // Der Standort ist per Konstruktion vorhanden (er stammt aus dem authentifizierten
        // Terminal-Token, siehe TerminalTokenService#createToken), daher hier ohne weitere
        // Fehlerbehandlung nachgeladen.
        LocationEntity location = this.locationRepository.findById(terminal.locationId()).orElseThrow();
        if (!this.permissionService.isUserAllowedAtLocation(user, location)) {
            throw new LocationNotAllowedException(user.getId(), location.getName());
        }

        return UserDto.of(user, this.creditService.getCredit(user));
    }
}
