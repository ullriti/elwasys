package org.kabieror.elwasys.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Idempotenz terminal-gemeldeter Execution-Ereignisse über den {@code Idempotency-Key}-Header
 * (AP3, Phase 4, siehe docs/kb/05-migration-plan.md "Idempotenz + Replay" und
 * {@link org.kabieror.elwasys.backend.api.idempotency.IdempotencyService} Javadoc).
 */
class ExecutionControllerIdempotencyTest extends AbstractApiIT {

    private record Setup(LocationEntity location, IssuedTerminalToken token, DeviceEntity device,
            ProgramEntity program, UserEntity user) {
    }

    private Setup fullyAllowedSetup(BigDecimal flagfall) {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        DeviceEntity device = newDevice(location);
        ProgramEntity program = newFixedProgram(flagfall);
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        allow(location, device, program, group);
        return new Setup(location, token, device, program, user);
    }

    private String startBody(Setup s) {
        return "{\"userId\":" + s.user().getId() + ",\"deviceId\":" + s.device().getId() + ",\"programId\":"
                + s.program().getId() + "}";
    }

    @Test
    void repeatedStartWithSameIdempotencyKeyCreatesOnlyOneExecution() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        String key = UUID.randomUUID().toString();

        MvcResult first = this.mockMvc.perform(
                post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(s))).andExpect(status().isCreated()).andReturn();
        String firstBody = first.getResponse().getContentAsString();

        MvcResult second = this.mockMvc.perform(
                post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(startBody(s))).andExpect(status().isCreated()).andReturn();
        String secondBody = second.getResponse().getContentAsString();

        assertThat(secondBody).isEqualTo(firstBody);
        assertThat(this.executionService.getRunningExecution(s.device())).isPresent();
        // Nur eine einzige Ausführung wurde tatsächlich angelegt (kein zweiter, konkurrierender
        // "device-occupied"-Fehler beim zweiten Aufruf, siehe DeviceOccupiedException).
    }

    @Test
    void repeatedStartWithoutIdempotencyKeyFailsOnTheSecondCallAsBefore() throws Exception {
        // Beweist Rückwärtskompatibilität: ohne Header verhält sich der Endpunkt exakt wie
        // vor AP3 (siehe ExecutionControllerTest#startOnAnOccupiedDeviceReturns409).
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isCreated());

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .contentType(MediaType.APPLICATION_JSON).content(startBody(s))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:elwasys:device-occupied"));
    }

    @Test
    void repeatedFinishWithSameIdempotencyKeyBooksThePriceOnlyOnce() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));
        String key = UUID.randomUUID().toString();

        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/finish").header("Authorization",
                authHeader(s.token())).header("Idempotency-Key", key)).andExpect(status().isOk());
        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/finish").header("Authorization",
                authHeader(s.token())).header("Idempotency-Key", key)).andExpect(status().isOk());

        assertThat(this.creditService.getCredit(s.user())).isEqualByComparingTo(new BigDecimal("47.00"));
    }

    @Test
    void differentIdempotencyKeysAreProcessedIndependently() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));

        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(startBody(s))).andExpect(status().isCreated());

        // Anderer Schlüssel, Gerät weiterhin belegt (die vorherige Ausführung läuft noch) ->
        // ein "neues" Ereignis wird ganz normal fachlich geprüft und schlägt hier erwartbar
        // mit 409 fehl (kein Idempotenz-Replay, weil der Schlüssel unbekannt ist).
        this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(s.token()))
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(startBody(s))).andExpect(status().isConflict());
    }

    @Test
    void resetIsIdempotentAndDoesNotBookTwice() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));
        String key = UUID.randomUUID().toString();

        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/reset").header("Authorization",
                authHeader(s.token())).header("Idempotency-Key", key)).andExpect(status().isOk());
        this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/reset").header("Authorization",
                authHeader(s.token())).header("Idempotency-Key", key)).andExpect(status().isOk());

        assertThat(this.creditService.getCredit(s.user())).isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
