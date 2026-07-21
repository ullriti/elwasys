package org.kabieror.elwasys.backend.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.shared.Registration;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.DeviceService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.kabieror.elwasys.backend.service.LocationService;
import org.kabieror.elwasys.backend.service.ProgramService;
import org.kabieror.elwasys.backend.service.UserGroupService;
import org.kabieror.elwasys.backend.service.UserService;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.kabieror.elwasys.backend.ui.push.UiBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-End-Nachweis der AP5-Ereignis-Kette (Service → {@code ApplicationEventPublisher} →
 * {@link UiBroadcaster} → registrierter Listener) über einen ECHTEN Spring-Kontext (siehe
 * kb/05-migration-plan.md, "Live-Updates zwischen Sessions") - bewusst über {@link
 * AbstractBackendIT} statt gemockter Services, damit auch Springs {@code
 * @TransactionalEventListener}-Mechanismus (Default-Phase {@code AFTER_COMMIT}) tatsächlich
 * mitgetestet wird, nicht nur der reine Verteil-Mechanismus von {@link UiBroadcaster} (der ist
 * Gegenstand von {@code UiBroadcasterTest}). KEIN Test rendert eine echte Vaadin-Route (siehe
 * kb/05-migration-plan.md, Lizenz-Befund) - {@link UI#access} wird wie in {@code
 * UiBroadcasterTest} über Mockito synchron simuliert.
 */
class DomainEventsTest extends AbstractBackendIT {

    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private DeviceRepository deviceRepository;
    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserService userService;
    @Autowired
    private UserGroupService userGroupService;
    @Autowired
    private DeviceService deviceService;
    @Autowired
    private ProgramService programService;
    @Autowired
    private LocationService locationService;
    @Autowired
    private CreditService creditService;
    @Autowired
    private ExecutionService executionService;
    @Autowired
    private UiBroadcaster broadcaster;

    private static UI mockUi() {
        UI ui = mock(UI.class);
        when(ui.access(any(Command.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Command.class).execute();
            Future<Void> future = CompletableFuture.completedFuture(null);
            return future;
        });
        return ui;
    }

    private UserGroupEntity group() {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
    }

    @Test
    void savingAUserPublishesAUserChangedEventThatTheBroadcasterDeliversToARegisteredListener() {
        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        UserEntity user = this.userService.create("Max Mustermann", Fixtures.unique("max"), null, new String[0],
                false, group());

        assertThat(received).contains(new UserChangedEvent(user.getId()));
    }

    @Test
    void savingAUserGroupPublishesAUserGroupChangedEvent() {
        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        UserGroupEntity group = this.userGroupService.create(Fixtures.unique("group"), DiscountType.NONE, 0);

        assertThat(received).contains(new UserGroupChangedEvent(group.getId()));
    }

    @Test
    void savingADevicePublishesADeviceChangedEvent() {
        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        DeviceEntity device = this.deviceService.create(Fixtures.unique("dev"), 1, location, "", "", "", "", 1f,
                Duration.ofSeconds(10), true, Set.of(), Set.of());

        assertThat(received).contains(new DeviceChangedEvent(device.getId()));
    }

    @Test
    void savingAProgramPublishesAProgramChangedEvent() {
        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        ProgramEntity program = this.programService.create(Fixtures.unique("prog"), ProgramType.FIXED,
                new BigDecimal("2.00"), BigDecimal.ZERO, null, Duration.ofMinutes(60), Duration.ZERO, false,
                Duration.ZERO, true, Set.of());

        assertThat(received).contains(new ProgramChangedEvent(program.getId()));
    }

    @Test
    void savingALocationPublishesALocationChangedEvent() {
        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        LocationEntity location = this.locationService.create(Fixtures.unique("loc"), Set.of());

        assertThat(received).contains(new LocationChangedEvent(location.getId()));
    }

    @Test
    void bookingCreditPublishesACreditChangedEventForTheAffectedUser() {
        UserEntity user = this.userService.create("Some User", Fixtures.unique("someuser"), null, new String[0],
                false, group());

        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        this.creditService.inpayment(user, new BigDecimal("10.00"));

        assertThat(received).contains(new CreditChangedEvent(user.getId()));
    }

    @Test
    void theFullExecutionLifecyclePublishesExecutionChangedEventsForTheAffectedDevice() {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 60);
        program.setFlagfall(new BigDecimal("3.00"));
        program = this.programRepository.save(program);
        DeviceEntity device = this.deviceRepository.save(
                new DeviceEntity(Fixtures.unique("dev"), 1, location));
        UserEntity user = this.userService.create("Runner", Fixtures.unique("runner"), null, new String[0], false,
                group());
        this.creditService.inpayment(user, new BigDecimal("100.00"));

        List<DomainEvent> received = new ArrayList<>();
        this.broadcaster.register(mockUi(), received::add);

        ExecutionEntity execution = this.executionService.createExecution(device, program, user);
        this.executionService.startExecution(execution);
        this.executionService.finishExecution(execution);

        assertThat(received).filteredOn(ExecutionChangedEvent.class::isInstance)
                .allSatisfy(event -> assertThat(((ExecutionChangedEvent) event).deviceId()).isEqualTo(
                        device.getId())).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void unregisteringStopsDeliveryEvenAcrossRealTransactions() {
        List<DomainEvent> received = new ArrayList<>();
        Registration registration = this.broadcaster.register(mockUi(), received::add);
        registration.remove();

        this.locationService.create(Fixtures.unique("loc"), Set.of());

        assertThat(received).isEmpty();
    }
}
