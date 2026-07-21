package org.kabieror.elwasys.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.support.AbstractApiIT;
import org.kabieror.elwasys.backend.support.Fixtures;

/**
 * Standort-Snapshot für die Offline-Buchungs-Vorbereitung (AP3, Phase 4, siehe
 * {@link org.kabieror.elwasys.backend.api.dto.SnapshotDto} Javadoc und
 * kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal").
 */
class SnapshotControllerTest extends AbstractApiIT {

    @Test
    void snapshotContainsOwnLocationDataAndNoPasswordField() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        UserGroupEntity group = newGroup();
        String cardId = Fixtures.unique("card");
        UserEntity user = newUserWithCard(group, cardId);
        allow(location, device, program, group);
        this.creditService.inpayment(user, new BigDecimal("7.50"));

        this.mockMvc.perform(get("/api/v1/snapshot").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$.locationId").value(location.getId())).andExpect(
                jsonPath("$.locationName").value(location.getName())).andExpect(
                jsonPath("$.generatedAt").exists()).andExpect(jsonPath("$.userGroups.length()").value(1)).andExpect(
                jsonPath("$.userGroups[0].id").value(group.getId())).andExpect(
                jsonPath("$.devices.length()").value(1)).andExpect(jsonPath("$.devices[0].id").value(device.getId()))
                .andExpect(jsonPath("$.devices[0].validUserGroupIds[0]").value(group.getId())).andExpect(
                jsonPath("$.devices[0].programIds[0]").value(program.getId())).andExpect(
                jsonPath("$.programs.length()").value(1)).andExpect(
                jsonPath("$.programs[0].id").value(program.getId())).andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].id").value(user.getId())).andExpect(
                jsonPath("$.users[0].cardIds[0]").value(cardId)).andExpect(
                jsonPath("$.users[0].credit").value(7.5)).andExpect(jsonPath("$.users[0].blocked").value(false))
                .andExpect(jsonPath("$.users[0].groupId").value(group.getId())).andExpect(
                jsonPath("$.users[0].password").doesNotExist());
    }

    @Test
    void snapshotDoesNotLeakUsersOfOtherLocations() throws Exception {
        LocationEntity ownLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        UserGroupEntity ownGroup = newGroup();
        ownLocation.getValidUserGroups().add(ownGroup);
        this.locationRepository.save(ownLocation);

        // Ein Benutzer, dessen Gruppe an einem ANDEREN Standort zugelassen ist, darf NICHT
        // im Snapshot dieses Standorts auftauchen.
        LocationEntity otherLocation = newLocation();
        UserGroupEntity otherGroup = newGroup();
        otherLocation.getValidUserGroups().add(otherGroup);
        this.locationRepository.save(otherLocation);
        newUser(otherGroup);

        this.mockMvc.perform(get("/api/v1/snapshot").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$.users.length()").value(0)).andExpect(
                jsonPath("$.userGroups.length()").value(1)).andExpect(
                jsonPath("$.userGroups[0].id").value(ownGroup.getId()));
    }

    @Test
    void snapshotDoesNotLeakDevicesOfOtherLocations() throws Exception {
        LocationEntity ownLocation = newLocation();
        LocationEntity otherLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        newDevice(otherLocation);

        this.mockMvc.perform(get("/api/v1/snapshot").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$.devices.length()").value(0)).andExpect(
                jsonPath("$.programs.length()").value(0));
    }

    @Test
    void snapshotOmitsDeletedAndBlockedButStillCorrectlyAnnotatedUsers() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        UserGroupEntity group = newGroup();
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);

        UserEntity blockedUser = newUser(group);
        blockedUser.setBlocked(true);
        this.userRepository.save(blockedUser);

        UserEntity deletedUser = newUser(group);
        deletedUser.setDeleted(true);
        this.userRepository.save(deletedUser);

        this.mockMvc.perform(get("/api/v1/snapshot").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$.users.length()").value(1)).andExpect(
                jsonPath("$.users[0].id").value(blockedUser.getId())).andExpect(
                jsonPath("$.users[0].blocked").value(true));
    }
}
