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
 * Geräte-/Programmliste über die Terminal-API (AP4, siehe kb/05-migration-plan.md).
 * Standort-Scope entspricht dem Client-E2E-Fall C16 ("standortfremdes Gerät erscheint nicht
 * in der Liste") - hier zusätzlich für den direkten Zugriff per Id (404 statt 403, siehe
 * {@link org.kabieror.elwasys.backend.api.exception.DeviceNotFoundException}).
 */
class DeviceControllerTest extends AbstractApiIT {

    @Test
    void listOnlyReturnsDevicesOfTheTokensOwnLocation() throws Exception {
        LocationEntity ownLocation = newLocation();
        LocationEntity otherLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        DeviceEntity ownDevice = newDevice(ownLocation);
        newDevice(otherLocation); // darf NICHT in der Antwort auftauchen

        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token))
                .param("userId", user.getId().toString())).andExpect(status().isOk()).andExpect(
                jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(ownDevice.getId()));
    }

    @Test
    void deviceOfAnotherLocationIsNotAccessibleById() throws Exception {
        LocationEntity ownLocation = newLocation();
        LocationEntity otherLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        DeviceEntity foreignDevice = newDevice(otherLocation);
        UserEntity user = newUser(newGroup());

        this.mockMvc.perform(get("/api/v1/devices/" + foreignDevice.getId()).header("Authorization",
                authHeader(token)).param("userId", user.getId().toString())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:elwasys:device-not-found"));
    }

    @Test
    void deviceListAnnotatesUsabilityAndOccupancyAndFiltersProgramsByPermission() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity allowedProgram = newFixedProgram(new BigDecimal("3.00"));
        ProgramEntity notAllowedProgram = newFixedProgram(new BigDecimal("5.00"));
        device.getPrograms().add(allowedProgram);
        device.getPrograms().add(notAllowedProgram);
        this.deviceRepository.save(device);

        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        allow(location, device, allowedProgram, group);
        // notAllowedProgram bleibt für die Gruppe des Benutzers nicht freigegeben.

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token))
                .param("userId", user.getId().toString())).andExpect(status().isOk()).andExpect(
                jsonPath("$[0].usableByUser").value(true)).andExpect(jsonPath("$[0].occupied").value(false))
                .andExpect(jsonPath("$[0].programs.length()").value(1)).andExpect(
                jsonPath("$[0].programs[0].id").value(allowedProgram.getId()));
    }

    @Test
    void deviceNotUsableByUsersGroupIsAnnotatedAccordingly() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location); // keine Gruppe freigegeben
        UserEntity user = newUser(newGroup());

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token))
                .param("userId", user.getId().toString())).andExpect(status().isOk()).andExpect(
                jsonPath("$[0].usableByUser").value(false));
    }

    @Test
    void unknownUserIdReturns404() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        newDevice(location);

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token)).param("userId",
                "-1")).andExpect(status().isNotFound()).andExpect(jsonPath("$.type").value(
                "urn:elwasys:user-not-found"));
    }

    @Test
    void missingUserIdParameterIsRejectedWith400() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token))).andExpect(
                status().isBadRequest());
    }

    @Test
    void occupiedDeviceIsAnnotatedAsSuch() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        allow(location, device, program, group);

        this.executionService.startExecution(this.executionService.createExecution(device, program, user));

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token))
                .param("userId", user.getId().toString())).andExpect(status().isOk()).andExpect(
                jsonPath("$[0].occupied").value(true));
    }
}
