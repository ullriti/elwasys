package org.kabieror.elwasys.backend.api;

import java.math.BigDecimal;
import java.time.Duration;
import org.kabieror.elwasys.backend.api.exception.DeviceNotUsableException;
import org.kabieror.elwasys.backend.api.exception.DeviceOccupiedException;
import org.kabieror.elwasys.backend.api.exception.InsufficientCreditException;
import org.kabieror.elwasys.backend.api.exception.LocationNotAllowedException;
import org.kabieror.elwasys.backend.api.exception.ProgramNotAvailableException;
import org.kabieror.elwasys.backend.api.exception.UserBlockedException;
import org.kabieror.elwasys.backend.api.exception.UserNotFoundException;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.AdvisoryLockService;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.springframework.stereotype.Component;

/**
 * Die fachlichen Wächter eines LIVE-Programmstarts (Sperrung/Standort/Nutzbarkeit/Belegung/
 * Guthaben), herausgelöst aus {@code ExecutionController#start} (Issue #90, finale Review R3a):
 * dort trug die Methode zwei Verantwortungen (HTTP-/Idempotenz-Orchestrierung und Fachregeln)
 * und war die einzige nennenswert überlange Methode des Backends. Die Regeln selbst sind
 * unverändert - inklusive Reihenfolge der Prüfungen und der daraus folgenden Fehlerabbildung
 * (siehe {@code ApiExceptionHandler}). Vorbild für einen fachlichen Wächter im {@code api}-Paket
 * ist {@link TerminalScopeGuard}.
 *
 * <p><b>Nicht</b> für den privilegierten Nachbuchungs-Pfad (Replay, Issue #16): eine
 * Offline-Nachmeldung ist ein Fakt und überspringt diese Wächter bewusst vollständig - siehe
 * {@code ExecutionController} Klassen-Javadoc.
 */
@Component
public class ExecutionStartGuard {

    private final UserRepository userRepository;

    private final PermissionService permissionService;

    private final PricingService pricingService;

    private final CreditService creditService;

    private final ExecutionService executionService;

    private final AdvisoryLockService advisoryLockService;

    public ExecutionStartGuard(UserRepository userRepository, PermissionService permissionService,
            PricingService pricingService, CreditService creditService, ExecutionService executionService,
            AdvisoryLockService advisoryLockService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.pricingService = pricingService;
        this.creditService = creditService;
        this.executionService = executionService;
        this.advisoryLockService = advisoryLockService;
    }

    /**
     * Serialisiert die Start-Entscheidung und prüft alle fachlichen Voraussetzungen eines
     * Live-Starts. Muss innerhalb einer laufenden Transaktion aufgerufen werden (die
     * Advisory-Lock-Sperre ist transaktionsgebunden, siehe {@link AdvisoryLockService}); über
     * die API ist das durch {@code IdempotencyService#execute} gegeben.
     *
     * @return der frisch geladene und bis zum Transaktionsende gesperrte Benutzer, auf dessen
     *         Stand die Wächter entschieden haben
     * @throws UserNotFoundException          wenn es den Benutzer nicht (mehr) gibt
     * @throws UserBlockedException           wenn der Benutzer gesperrt ist
     * @throws LocationNotAllowedException    wenn seine Gruppe am Standort nicht zugelassen ist
     * @throws DeviceNotUsableException       wenn das Gerät deaktiviert oder für seine Gruppe
     *                                        nicht freigegeben ist
     * @throws ProgramNotAvailableException   wenn das Programm für Gerät/Gruppe nicht verfügbar ist
     * @throws DeviceOccupiedException        wenn auf dem Gerät bereits eine Ausführung läuft
     * @throws InsufficientCreditException    wenn das Guthaben den Maximalpreis nicht deckt
     */
    public UserEntity lockAndValidate(DeviceEntity device, ProgramEntity program, Integer userId) {
        // Issue #20: Belegungs- und Guthabenentscheidung serialisieren, damit zwei parallele
        // Starts nicht beide ein freies Gerät bzw. ausreichendes Guthaben sehen und doppelt
        // belegen/reservieren. Reihenfolge bewusst erst Gerät (Advisory-Lock), dann Nutzer
        // (Zeilensperre) - konsistent zu den übrigen Geldpfaden, um Deadlocks auszuschließen.
        this.advisoryLockService.lockDevice(device.getId());
        // Nutzer FRISCH und pessimistisch GESPERRT laden (nicht vorab per findById): so
        // entscheiden die Wächter (isBlocked/Rechte) und der Guthabencheck auf dem Stand NACH
        // Lock-Erwerb, nicht auf einem davor gelesenen Snapshot.
        UserEntity user = this.userRepository.findWithLockById(userId).orElseThrow(
                () -> new UserNotFoundException(userId));
        if (user.isBlocked()) {
            throw new UserBlockedException(user.getId());
        }
        if (!this.permissionService.isUserAllowedAtLocation(user, device.getLocation())) {
            throw new LocationNotAllowedException(user.getId(), device.getLocation().getName());
        }
        if (!this.permissionService.isDeviceUsableByUser(device, user)) {
            throw new DeviceNotUsableException(device.getId(), user.getId());
        }
        if (!this.permissionService.isProgramAvailableForDeviceAndUser(device, program, user)) {
            throw new ProgramNotAvailableException(program.getId(), device.getId(), user.getId());
        }
        if (this.executionService.getRunningExecution(device).isPresent()) {
            throw new DeviceOccupiedException(device.getId());
        }
        BigDecimal maxPrice = this.pricingService.getPrice(program,
                Duration.ofSeconds(program.getMaxDurationSeconds()), user);
        if (!this.creditService.canAfford(user, maxPrice)) {
            throw new InsufficientCreditException(user.getId(), maxPrice, this.creditService.getCredit(user));
        }
        return user;
    }
}
