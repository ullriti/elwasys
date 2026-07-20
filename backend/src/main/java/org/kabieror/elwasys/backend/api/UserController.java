package org.kabieror.elwasys.backend.api;

import org.kabieror.elwasys.backend.api.dto.CreditResponse;
import org.kabieror.elwasys.backend.api.exception.UserNotFoundException;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guthabenabfrage (AP4, siehe kb/05-migration-plan.md), entspricht {@code User#getCredit()}
 * im Alt-Code. Bewusst NICHT auf den Standort des Terminal-Tokens beschränkt: Guthaben ist
 * eine personenbezogene, standortunabhängige Größe (im Gegensatz zu Geräten/Ausführungen,
 * siehe {@link TerminalScopeGuard}) - jedes gültige Terminal-Token darf das Guthaben eines
 * beliebigen Benutzers abfragen, genau wie der Alt-Code keine Standortbindung für
 * {@code User#getCredit()} kennt.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    private final CreditService creditService;

    public UserController(UserRepository userRepository, CreditService creditService) {
        this.userRepository = userRepository;
        this.creditService = creditService;
    }

    @GetMapping("/{id}/credit")
    public CreditResponse credit(@PathVariable Integer id) {
        UserEntity user = this.userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        return new CreditResponse(user.getId(), this.creditService.getCredit(user));
    }
}
