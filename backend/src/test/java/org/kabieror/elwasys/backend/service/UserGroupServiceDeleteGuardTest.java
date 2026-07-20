package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reiner Unit-Test (Mockito, keine DB) für den in {@link UserGroupServiceTest} nicht
 * herstellbaren Randfall: es gibt keine andere Benutzergruppe, der die Benutzer der zu
 * löschenden Gruppe zugewiesen werden könnten. Der Alt-Code
 * ({@code Common.UserGroup#delete}) hätte hier einen NOT-NULL-Constraint-Verstoß erzeugt
 * (siehe {@link UserGroupService#delete} Javadoc); dieser Service prüft stattdessen vorab
 * und wirft einen sprechenden {@link EntityInUseException}.
 */
@ExtendWith(MockitoExtension.class)
class UserGroupServiceDeleteGuardTest {

    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ProgramRepository programRepository;

    @Test
    void deletingTheLastRemainingGroupThrowsAndDoesNotTouchUsersOrDeleteTheGroup() {
        UserGroupService service = new UserGroupService(this.userGroupRepository, this.userRepository,
                this.locationRepository, this.deviceRepository, this.programRepository);
        UserGroupEntity onlyGroup = new UserGroupEntity("Only Group", DiscountType.NONE, 0);

        when(this.userGroupRepository.findFirstByIdNotOrderByIdAsc(onlyGroup.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(onlyGroup)).isInstanceOf(EntityInUseException.class);

        verifyNoInteractions(this.userRepository);
    }
}
