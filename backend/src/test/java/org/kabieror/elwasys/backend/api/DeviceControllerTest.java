package org.kabieror.elwasys.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.support.AbstractApiIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.http.MediaType;

/**
 * Geräte-/Programmliste über die Terminal-API (AP4, siehe docs/kb/05-migration-plan.md).
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

    @Test
    void deviceDtoIncludesGatewayConfigurationFieldsForTheTerminal() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        device.setFhemName("wm1");
        device.setFhemSwitchName("wm1sw");
        device.setFhemPowerName("wm1pw");
        device.setDeconzUuid("abc-123");
        device.setAutoEndPowerThreshold(1.5f);
        device.setAutoEndWaitTimeSeconds(42);
        this.deviceRepository.save(device);
        UserEntity user = newUser(newGroup());

        this.mockMvc.perform(get("/api/v1/devices").header("Authorization", authHeader(token))
                .param("userId", user.getId().toString())).andExpect(status().isOk()).andExpect(
                jsonPath("$[0].fhemName").value("wm1")).andExpect(jsonPath("$[0].fhemSwitchName").value("wm1sw"))
                .andExpect(jsonPath("$[0].fhemPowerName").value("wm1pw")).andExpect(
                jsonPath("$[0].deconzUuid").value("abc-123")).andExpect(
                jsonPath("$[0].autoEndPowerThreshold").value(1.5)).andExpect(
                jsonPath("$[0].autoEndWaitTimeSeconds").value(42));
    }

    /**
     * {@code GET /api/v1/devices/overview} (AP3, Phase 4, siehe {@link
     * org.kabieror.elwasys.backend.api.dto.DeviceOverviewDto} Javadoc): die anonyme
     * Geräteübersicht, die der Client vor einem Kartenlogin braucht (Zustand
     * {@code SELECT_DEVICE}).
     */
    @Test
    void overviewListsOwnLocationDevicesWithoutRequiringAUserId() throws Exception {
        LocationEntity ownLocation = newLocation();
        LocationEntity otherLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        DeviceEntity ownDevice = newDevice(ownLocation);
        newDevice(otherLocation); // darf NICHT in der Antwort auftauchen

        this.mockMvc.perform(get("/api/v1/devices/overview").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(
                jsonPath("$[0].id").value(ownDevice.getId())).andExpect(jsonPath("$[0].occupied").value(false))
                .andExpect(jsonPath("$[0].runningExecutionId").doesNotExist()).andExpect(
                jsonPath("$[0].lastUserId").doesNotExist());
    }

    @Test
    void overviewAnnotatesOccupiedDeviceWithTheRunningExecutionId() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        allow(location, device, program, group);
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(device, program, user));

        this.mockMvc.perform(get("/api/v1/devices/overview").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$[0].occupied").value(true)).andExpect(
                jsonPath("$[0].runningExecutionId").value(execution.getId()));
    }

    @Test
    void overviewAnnotatesTheLastUserOfADevice() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        allow(location, device, program, group);
        ExecutionEntity execution = this.executionService.finishExecution(
                this.executionService.startExecution(this.executionService.createExecution(device, program, user)));
        org.assertj.core.api.Assertions.assertThat(execution.isFinished()).isTrue();

        this.mockMvc.perform(get("/api/v1/devices/overview").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$[0].occupied").value(false)).andExpect(
                jsonPath("$[0].lastUserId").value(user.getId())).andExpect(
                jsonPath("$[0].lastUserName").value(user.getName()));
    }

    @Test
    void overviewIncludesGatewayConfigurationFieldsWithoutAUser() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        device.setFhemSwitchName("wm2sw");
        this.deviceRepository.save(device);

        this.mockMvc.perform(get("/api/v1/devices/overview").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$[0].fhemSwitchName").value("wm2sw"));
    }

    /**
     * Phase 4 AP4 (additiv): {@code ui/small} zeigt Programme samt Preis bereits VOR dem
     * Kartenlogin an (siehe {@link org.kabieror.elwasys.backend.api.dto.DeviceOverviewDto}
     * Javadoc) - der Preis muss dabei ohne Gruppenrabatt berechnet werden (Alt-Code:
     * {@code User.getAnonymous()}).
     */
    @Test
    void overviewIncludesProgramsWithUndiscountedPricing() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        UserGroupEntity group = newGroup();
        allow(location, device, program, group);

        this.mockMvc.perform(get("/api/v1/devices/overview").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$[0].programs.length()").value(1)).andExpect(
                jsonPath("$[0].programs[0].id").value(program.getId())).andExpect(
                jsonPath("$[0].programs[0].priceAtMaxDuration").value(3.00));
    }

    /**
     * Phase 4 AP4 (additiv, siehe docs/kb/05-migration-plan.md Änderungslog): fachlicher
     * Nachfolger von {@code Device#modify(...)}, den der Client-Alt-Code
     * ({@code DeconzRegistrationService#registerDevice}) nach einer erfolgreichen
     * Pairing-Suche aufruft, um die neu gefundene deCONZ-Geräte-Id zu hinterlegen. Die
     * AP3-Inventur hatte diesen (untesteten, Admin-Registrierungs-)Pfad übersehen.
     */
    @Test
    void deconzUuidCanBeUpdatedForADeviceOfTheTokensOwnLocation() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);

        this.mockMvc.perform(post("/api/v1/devices/" + device.getId() + "/deconz-uuid").header("Authorization",
                authHeader(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deconzUuid\":\"new-uuid-123\"}")).andExpect(status().isOk()).andExpect(
                jsonPath("$.deconzUuid").value("new-uuid-123"));

        this.mockMvc.perform(get("/api/v1/devices/overview").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$[0].deconzUuid").value("new-uuid-123"));
    }

    /**
     * Issue #42 (Pre-Launch AP4): der deconz-uuid-Endpunkt validiert den Body - ein leerer
     * Wert wird mit {@code 400} abgewiesen, statt eine unbrauchbare Kennung zu speichern.
     */
    @Test
    void deconzUuidUpdateWithABlankValueIsRejectedWith400() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);

        this.mockMvc.perform(post("/api/v1/devices/" + device.getId() + "/deconz-uuid").header("Authorization",
                authHeader(token)).contentType(MediaType.APPLICATION_JSON).content("{\"deconzUuid\":\"\"}")).andExpect(
                status().isBadRequest());
    }

    @Test
    void deconzUuidUpdateWithAnOversizedValueIsRejectedWith400() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);

        String oversized = "x".repeat(65); // @Size(max = 64)
        this.mockMvc.perform(post("/api/v1/devices/" + device.getId() + "/deconz-uuid").header("Authorization",
                authHeader(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deconzUuid\":\"" + oversized + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void deconzUuidOfAForeignDeviceIsNotAccessible() throws Exception {
        LocationEntity ownLocation = newLocation();
        LocationEntity otherLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        DeviceEntity foreignDevice = newDevice(otherLocation);

        this.mockMvc.perform(post("/api/v1/devices/" + foreignDevice.getId() + "/deconz-uuid").header("Authorization",
                authHeader(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"deconzUuid\":\"new-uuid-123\"}")).andExpect(status().isNotFound());
    }
}
