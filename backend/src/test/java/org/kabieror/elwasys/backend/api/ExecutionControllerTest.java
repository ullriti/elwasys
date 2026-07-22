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
import org.springframework.http.MediaType;

/**
 * Execution-Lebenszyklus über die Terminal-API (AP4, siehe docs/kb/05-migration-plan.md):
 * starten/beenden/abbrechen/zurücksetzen inkl. Abrechnung sowie die fachlichen Fehlerfälle
 * (Standort fremdes Gerät, Gerät nicht nutzbar, Programm nicht verfügbar, Gerät belegt,
 * Guthaben nicht ausreichend), orientiert an den Client-E2E-Fällen C9 (zu wenig Guthaben) und
 * C16 (standortfremdes Gerät).
 */
class ExecutionControllerTest extends AbstractApiIT {

    private record Setup(LocationEntity location, IssuedTerminalToken token, DeviceEntity device,
            ProgramEntity program, UserGroupEntity group, UserEntity user) {
    }

    private Setup fullyAllowedSetup(BigDecimal flagfall) {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(flagfall);
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        allow(location, device, program, group);
        return new Setup(location, token, device, program, group, user);
    }

    private String startBody(Setup s) {
        return "{\"userId\":" + s.user().getId() + ",\"deviceId\":" + s.device().getId() + ",\"programId\":"
                + s.program().getId() + "}";
    }

    @Test
    void startCreatesAndStartsAnExecution() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.deviceId").value(s.device().getId())).andExpect(
                jsonPath("$.programId").value(s.program().getId())).andExpect(jsonPath("$.finished").value(false))
                .andExpect(jsonPath("$.start").exists());
    }

    @Test
    void startOnAForeignLocationsDeviceReturns404() throws Exception {
        LocationEntity ownLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        Setup foreign = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(foreign.user(), new BigDecimal("50.00"));

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(foreign))).andExpect(
                status().isNotFound()).andExpect(jsonPath("$.type").value("urn:elwasys:device-not-found"));
    }

    @Test
    void startWithBlockedUserReturns403() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        s.user().setBlocked(true);
        this.userRepository.save(s.user());

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:elwasys:user-blocked"));
    }

    @Test
    void startWithDeviceNotUsableByUsersGroupReturns403() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location); // Gruppe NICHT freigegeben
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        device.getPrograms().add(program);
        this.deviceRepository.save(device);
        UserGroupEntity group = newGroup();
        program.getValidUserGroups().add(group);
        this.programRepository.save(program);
        location.getValidUserGroups().add(group); // Standort erlaubt, Gerät bewusst NICHT
        this.locationRepository.save(location);
        UserEntity user = newUser(group);

        Setup s = new Setup(location, token, device, program, group, user);
        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:elwasys:device-not-usable"));
    }

    @Test
    void startWithProgramNotAvailableForDeviceReturns403() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00")); // NICHT dem Gerät zugeordnet
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        device.getValidUserGroups().add(group);
        this.deviceRepository.save(device);
        program.getValidUserGroups().add(group);
        this.programRepository.save(program);
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);

        Setup s = new Setup(location, token, device, program, group, user);
        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:elwasys:program-not-available"));
    }

    @Test
    void startOnAnOccupiedDeviceReturns409() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:elwasys:device-occupied"));
    }

    @Test
    void startWithoutEnoughCreditReturns402() throws Exception {
        // Entspricht dem Client-E2E-Fall C9 ("zu wenig Guthaben"): kein Startbutton im Alt-
        // Code, hier eine serverseitige 402-Ablehnung.
        Setup s = fullyAllowedSetup(new BigDecimal("30.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("1.00"));

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(
                status().isPaymentRequired()).andExpect(jsonPath("$.type").value("urn:elwasys:insufficient-credit"));
    }

    @Test
    void finishStopsTheExecutionAndBooksThePrice() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));

        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/finish").header("Authorization",
                authHeader(s.token()))).andExpect(status().isOk()).andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.stop").exists());

        assertCreditIsExactly(s.user(), new BigDecimal("47.00"));
    }

    @Test
    void abortStopsTheExecutionLikeFinish() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));

        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/abort").header("Authorization",
                authHeader(s.token()))).andExpect(status().isOk()).andExpect(jsonPath("$.finished").value(true));
    }

    @Test
    void finishingAnAlreadyFinishedExecutionReturns409() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.finishExecution(
                this.executionService.startExecution(
                        this.executionService.createExecution(s.device(), s.program(), s.user())));

        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/finish").header("Authorization",
                authHeader(s.token()))).andExpect(status().isConflict()).andExpect(
                jsonPath("$.type").value("urn:elwasys:execution-already-finished"));
    }

    @Test
    void resetClearsStartAndStopWithoutBooking() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));

        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/reset").header("Authorization",
                authHeader(s.token()))).andExpect(status().isOk()).andExpect(jsonPath("$.start").doesNotExist())
                .andExpect(jsonPath("$.finished").value(true));

        assertCreditIsExactly(s.user(), new BigDecimal("50.00"));
    }

    @Test
    void executionOfAForeignLocationIsNotAccessible() throws Exception {
        LocationEntity ownLocation = newLocation();
        IssuedTerminalToken token = newToken(ownLocation);
        Setup foreign = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(foreign.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(foreign.device(), foreign.program(), foreign.user()));

        this.mockMvc.perform(get("/api/v1/executions/" + execution.getId()).header("Authorization",
                authHeader(token))).andExpect(status().isNotFound()).andExpect(
                jsonPath("$.type").value("urn:elwasys:execution-not-found"));
    }

    @Test
    void getReturnsTheCurrentExecutionState() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));

        this.mockMvc.perform(get("/api/v1/executions/" + execution.getId()).header("Authorization",
                authHeader(s.token()))).andExpect(status().isOk()).andExpect(
                jsonPath("$.id").value(execution.getId())).andExpect(jsonPath("$.finished").value(false));
    }

    private void assertCreditIsExactly(UserEntity user, BigDecimal expected) {
        org.assertj.core.api.Assertions.assertThat(this.creditService.getCredit(user)).isEqualByComparingTo(expected);
    }
}
