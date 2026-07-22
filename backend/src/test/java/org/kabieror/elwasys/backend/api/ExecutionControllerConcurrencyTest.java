package org.kabieror.elwasys.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * Nebenläufigkeitstests der Terminal-Execution-API (Issue #20, #29 - AP3). Belegen, dass die
 * in AP3 eingezogenen Sperren (Geräte-Advisory-Lock im Start-Pfad, pessimistische
 * Zeilensperre der Ausführung im Finish-Pfad, Idempotenz-Schlüssel-Advisory-Lock) genau die
 * Races schließen, die das Mehrbenutzer-Backend vom Alt-System (ein Client je Gerät)
 * unterscheiden: Doppelstart/Überbuchung, Doppelabrechnung und der Idempotenz-Race, der
 * bisher einen HTTP 500 liefern konnte.
 */
class ExecutionControllerConcurrencyTest extends AbstractApiIT {

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
        return startBody(s.user(), s.device(), s.program());
    }

    private String startBody(UserEntity user, DeviceEntity device, ProgramEntity program) {
        return "{\"userId\":" + user.getId() + ",\"deviceId\":" + device.getId() + ",\"programId\":"
                + program.getId() + "}";
    }

    /**
     * Führt {@code count} identische Aufrufe echt gleichzeitig aus (alle warten an derselben
     * Startlinie) und gibt die HTTP-Statuscodes zurück.
     */
    private List<Integer> runConcurrently(int count, Callable<Integer> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return call.call();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();
        return statuses;
    }

    @Test
    void concurrentStartsOnTheSameDeviceCreateExactlyOneExecution() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        int threads = 6;

        List<Integer> statuses = runConcurrently(threads, () -> {
            MvcResult result = this.mockMvc.perform(post("/api/v1/executions")
                    .header("Authorization", authHeader(s.token())).contentType(MediaType.APPLICATION_JSON)
                    .content(startBody(s))).andReturn();
            return result.getResponse().getStatus();
        });

        // Genau ein Start gewinnt (201), alle anderen sehen das Gerät belegt (409) - und es
        // existiert tatsächlich nur EINE gestartete Ausführung auf dem Gerät.
        assertThat(statuses.stream().filter(st -> st == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(st -> st == 409).count()).isEqualTo(threads - 1L);
        assertThat(this.executionService.getExecutions(s.device())).hasSize(1);
    }

    @Test
    void concurrentStartsForTheSameUserOnDifferentDevicesReserveCreditOnlyOnce() throws Exception {
        // Issue #20 (Guthaben-Reservierung): zwei parallele Starts desselben Nutzers auf
        // VERSCHIEDENEN Geräten - der Geräte-Advisory-Lock hilft hier nicht (andere Geräte),
        // nur die pessimistische Nutzer-Zeilensperre serialisiert die Reservierung. Guthaben
        // reicht für GENAU einen Start (maxPrice = Flagfall 3.00).
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        ProgramEntity program = newFixedProgram(new BigDecimal("3.00"));
        UserGroupEntity group = newGroup();
        UserEntity user = newUser(group);
        DeviceEntity deviceA = newDevice(location);
        DeviceEntity deviceB = newDevice(location);
        allow(location, deviceA, program, group);
        allow(location, deviceB, program, group);
        this.creditService.inpayment(user, new BigDecimal("3.00"));

        String bodyA = startBody(user, deviceA, program);
        String bodyB = startBody(user, deviceB, program);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> callA = () -> {
            ready.countDown();
            go.await();
            return this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON).content(bodyA)).andReturn().getResponse().getStatus();
        };
        Callable<Integer> callB = () -> {
            ready.countDown();
            go.await();
            return this.mockMvc.perform(post("/api/v1/executions").header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON).content(bodyB)).andReturn().getResponse().getStatus();
        };
        Future<Integer> fa = pool.submit(callA);
        Future<Integer> fb = pool.submit(callB);
        ready.await();
        go.countDown();
        List<Integer> statuses = List.of(fa.get(), fb.get());
        pool.shutdown();

        // Genau ein Start wird angelegt (201), der andere sieht das reservierte Guthaben und
        // wird mit 402 abgelehnt - und das Guthaben bleibt bei 0.00 (nicht -3.00).
        assertThat(statuses.stream().filter(st -> st == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(st -> st == 402).count()).isEqualTo(1);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentFinishesBookThePriceExactlyOnce() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));
        int threads = 6;

        List<Integer> statuses = runConcurrently(threads, () -> {
            MvcResult result = this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/finish")
                    .header("Authorization", authHeader(s.token()))).andReturn();
            return result.getResponse().getStatus();
        });

        // Genau ein finish beendet und bucht (200), alle anderen sehen "bereits beendet" (409).
        assertThat(statuses.stream().filter(st -> st == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(st -> st == 409).count()).isEqualTo(threads - 1L);
        // Der Preis (Flagfall 3.00) wurde GENAU EINMAL abgebucht.
        assertThat(this.creditService.getCredit(s.user())).isEqualByComparingTo("47.00");
    }

    @Test
    void concurrentFinishesWithTheSameIdempotencyKeyBookOnceAndNeverReturn500() throws Exception {
        Setup s = fullyAllowedSetup(new BigDecimal("3.00"));
        this.creditService.inpayment(s.user(), new BigDecimal("50.00"));
        ExecutionEntity execution = this.executionService.startExecution(
                this.executionService.createExecution(s.device(), s.program(), s.user()));
        String key = UUID.randomUUID().toString();
        int threads = 6;

        List<Integer> statuses = runConcurrently(threads, () -> {
            MvcResult result = this.mockMvc.perform(post("/api/v1/executions/" + execution.getId() + "/finish")
                    .header("Authorization", authHeader(s.token())).header("Idempotency-Key", key)).andReturn();
            return result.getResponse().getStatus();
        });

        // Alle Anfragen liefern 200 (der Erste beendet, die übrigen erhalten die gespeicherte
        // Antwort per Replay) - insbesondere KEIN 500 durch eine an der Unique-Constraint
        // vergiftete Transaktion (Issue #29).
        assertThat(statuses).allMatch(st -> st == 200);
        // Und trotz sechs paralleler Aufrufe wurde der Preis nur EINMAL gebucht.
        assertThat(this.creditService.getCredit(s.user())).isEqualByComparingTo("47.00");
    }
}
