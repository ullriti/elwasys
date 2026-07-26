package org.kabieror.elwasys.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.api.exception.DeviceNotUsableException;
import org.kabieror.elwasys.backend.api.exception.DeviceOccupiedException;
import org.kabieror.elwasys.backend.api.exception.InsufficientCreditException;
import org.kabieror.elwasys.backend.api.exception.LocationNotAllowedException;
import org.kabieror.elwasys.backend.api.exception.ProgramNotAvailableException;
import org.kabieror.elwasys.backend.api.exception.UserBlockedException;
import org.kabieror.elwasys.backend.api.exception.UserNotFoundException;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.AdvisoryLockService;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.PermissionService;
import org.kabieror.elwasys.backend.service.PricingService;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Die fachlichen Start-Wächter isoliert (Issue #90: aus {@code ExecutionController#start}
 * herausgelöst, siehe {@link ExecutionStartGuard}). Die Fehlerabbildung auf HTTP-Status prüft
 * weiterhin {@link ExecutionControllerTest} end-to-end; hier geht es um die Regeln selbst -
 * insbesondere um die REIHENFOLGE (Sperren vor Prüfungen, Sperrung vor Standort/Gerät/
 * Programm/Belegung/Guthaben), die sich bei einem Umbau unbemerkt verschieben könnte, und um
 * den Standort-Wächter, den bisher kein Testfall abdeckte.
 *
 * <p>Bewusst ein reiner Mockito-Unit-Test ohne Spring-Kontext (Begründung siehe
 * {@link ExecutionControllerNotificationTest}); {@link PermissionService}/
 * {@link PricingService} sind abhängigkeitsfreie Fachlogik und werden daher ECHT verwendet.
 */
class ExecutionStartGuardTest {

    private static final Integer USER_ID = 42;

    private UserRepository userRepository;

    private CreditService creditService;

    private ExecutionService executionService;

    private AdvisoryLockService advisoryLockService;

    private ExecutionStartGuard guard;

    private LocationEntity location;

    private DeviceEntity device;

    private ProgramEntity program;

    private UserGroupEntity group;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        this.userRepository = mock(UserRepository.class);
        this.creditService = mock(CreditService.class);
        this.executionService = mock(ExecutionService.class);
        this.advisoryLockService = mock(AdvisoryLockService.class);
        this.guard = new ExecutionStartGuard(this.userRepository, new PermissionService(), new PricingService(),
                this.creditService, this.executionService, this.advisoryLockService);

        this.group = new UserGroupEntity("Testgruppe", DiscountType.NONE, 0);
        this.location = new LocationEntity("Waschkeller");
        this.location.getValidUserGroups().add(this.group);
        this.device = new DeviceEntity("Waschmaschine 1", 0, this.location);
        this.device.getValidUserGroups().add(this.group);
        this.program = new ProgramEntity("Kurzprogramm", ProgramType.FIXED, 3600);
        this.program.setFlagfall(new BigDecimal("2.50"));
        this.program.setFreeDurationSeconds(0);
        this.program.getValidUserGroups().add(this.group);
        this.device.getPrograms().add(this.program);
        this.user = new UserEntity("Erika Mustermann", "erika", this.group);
        // Ids setzen wie in ExecutionControllerOfflineReplayTest: die Wächter reichen sie an
        // AdvisoryLockService#lockDevice(int) und in die Fehlermeldungen weiter, ohne Persistenz
        // bleiben sie sonst null (NPE beim Unboxing).
        ReflectionTestUtils.setField(this.location, "id", 1);
        ReflectionTestUtils.setField(this.device, "id", 2);
        ReflectionTestUtils.setField(this.program, "id", 3);
        ReflectionTestUtils.setField(this.group, "id", 4);
        ReflectionTestUtils.setField(this.user, "id", USER_ID);

        when(this.userRepository.findWithLockById(USER_ID)).thenReturn(Optional.of(this.user));
        when(this.executionService.getRunningExecution(any())).thenReturn(Optional.empty());
        when(this.creditService.canAfford(any(), any())).thenReturn(true);
    }

    @Test
    void allGuardsPassedReturnsTheFreshlyLockedUserAndLocksDeviceBeforeUser() {
        UserEntity result = this.guard.lockAndValidate(this.device, this.program, USER_ID);

        assertThat(result).isSameAs(this.user);
        // Reihenfolge Gerät (Advisory-Lock) vor Nutzer (Zeilensperre) - Issue #20, bewusst
        // konsistent zu den übrigen Geldpfaden, um Deadlocks auszuschließen.
        InOrder inOrder = inOrder(this.advisoryLockService, this.userRepository);
        inOrder.verify(this.advisoryLockService).lockDevice(this.device.getId());
        inOrder.verify(this.userRepository).findWithLockById(USER_ID);
    }

    @Test
    void unknownUserIsRejected() {
        when(this.userRepository.findWithLockById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void blockedUserIsRejected() {
        this.user.setBlocked(true);

        assertThrows(UserBlockedException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void userWhoseGroupIsNotAllowedAtTheLocationIsRejected() {
        this.location.getValidUserGroups().remove(this.group);

        assertThrows(LocationNotAllowedException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void disabledDeviceIsRejected() {
        this.device.setEnabled(false);

        assertThrows(DeviceNotUsableException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void programNotAvailableForTheDeviceIsRejected() {
        this.device.getPrograms().remove(this.program);

        assertThrows(ProgramNotAvailableException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void occupiedDeviceIsRejected() {
        when(this.executionService.getRunningExecution(this.device)).thenReturn(
                Optional.of(new ExecutionEntity(this.device, this.program, this.user)));

        assertThrows(DeviceOccupiedException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void insufficientCreditIsRejected() {
        when(this.creditService.canAfford(any(), any())).thenReturn(false);
        when(this.creditService.getCredit(this.user)).thenReturn(new BigDecimal("0.50"));

        assertThrows(InsufficientCreditException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }

    @Test
    void aBlockedUserIsRejectedBeforeTheLocationIsEvenChecked() {
        // Gegenprobe zur Reihenfolge: ein gesperrter Nutzer an einem für ihn ohnehin nicht
        // zugelassenen Standort meldet weiterhin "gesperrt" (403 UserBlocked), nicht
        // "Standort nicht erlaubt" - die Fehlermeldung am Terminal bleibt damit unverändert.
        this.user.setBlocked(true);
        this.location.getValidUserGroups().remove(this.group);

        assertThrows(UserBlockedException.class, () -> this.guard.lockAndValidate(this.device, this.program,
                USER_ID));
    }
}
