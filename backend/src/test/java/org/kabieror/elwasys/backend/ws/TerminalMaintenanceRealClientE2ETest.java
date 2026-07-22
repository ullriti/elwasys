package org.kabieror.elwasys.backend.ws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.kabieror.elwasys.backend.support.TestPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Phase 4 AP5 - Nachfolger der alten Cross-Component-Suite (Testplan P21/P22,
 * {@code Client-Raspi/run-cross-component-e2e.sh} vor diesem Arbeitspaket): die alte Suite
 * prüfte die Fernwartungsverbindung über das Alt-TCP-Protokoll ({@code Common.maintenance.*},
 * Portal wählte den als Server lauschenden Client an). Seit AP5 hält das Terminal stattdessen
 * eine ausgehende WebSocket-Verbindung zum Backend ({@code TerminalWebSocketClient} im
 * Client-Raspi-Modul) - diese Suite beweist Status/Log/Restart end-to-end über GENAU diesen
 * neuen Weg: {@link TerminalMaintenanceService} (die Portal-seitige Vermittlung, siehe
 * docs/kb/03-modules.md) &rarr; {@code TerminalWebSocketHandler} (Backend-WS) &rarr; ein ECHTER,
 * als Subprozess gestarteter Client-Raspi-Prozess &rarr; Antwort zurück.
 *
 * <p><b>Aufbau ("kleinster belastbarer Aufbau", siehe AP5-Auftrag)</b>: dieser Test bootet den
 * Backend-Spring-Kontext SELBST ({@code @SpringBootTest(webEnvironment=RANDOM_PORT)}, wie
 * {@link TerminalWebSocketTest}), damit {@link TerminalMaintenanceService} als ECHTE,
 * Spring-verwaltete Bean direkt aufgerufen werden kann - das ist die Portal-seitige
 * Vermittlung, exakt wie sie {@code AdminDashboardView} (Phase 3 AP4) im Produktivbetrieb
 * verwendet, nur ohne den Umweg über eine Browser-UI. Der "Client" ist dagegen bewusst KEIN
 * Test-Double, sondern der ECHTE, gepackte {@code raspi-client-*-jar-with-dependencies.jar}
 * (gebaut von {@code run-cross-component-e2e.sh}), als Subprozess gestartet und über
 * {@code -dry} (siehe {@code Main#dry}/{@code FhemDevicePowerManager}) OHNE echtes
 * Gateway betrieben - dieser Test prüft die Fernwartungsverbindung, nicht die
 * Gateway-Anbindung (die hat eigene E2E-Abdeckung, siehe
 * {@code Client*Deconz/Fhem*E2ETest}). Läuft unter {@code xvfb-run} (siehe
 * {@code run-cross-component-e2e.sh}), weil der reale Client-Prozess JavaFX braucht.
 *
 * <p><b>Restart-Test</b>: {@link TerminalMaintenanceService#requestRestart} wird gegen den
 * ECHTEN Client aufgerufen und muss - wie im Produktivbetrieb - mit einer echten
 * {@code RESTART_RESPONSE} vom Terminal quittiert werden, BEVOR {@code ElwaManager#restart()}
 * im Client-Prozess läuft. Der Test verifiziert absichtlich NUR diese Bestätigung plus dass
 * die WebSocket-Verbindung den Neustart überlebt (siehe {@code TerminalWebSocketClient#onClose},
 * das bei {@code restart=true} bewusst NICHT abbaut) - er tötet den Subprozess NICHT und
 * beobachtet auch keinen OS-Prozess-Neustart: "Neustart" bedeutet in dieser Anwendung seit
 * jeher ein In-Prozess-Reinitialisieren des Hauptfensters (siehe
 * {@code ElwaManager#restart}), keinen echten Prozess-Neustart - identisch zum Umfang, den
 * die alte {@code ClientMaintenanceConnectionE2ETest} (vor diesem Arbeitspaket) über einen
 * Close-Listener-Flag geprüft hat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class TerminalMaintenanceRealClientE2ETest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TerminalTokenService terminalTokenService;

    @Autowired
    private TerminalMaintenanceService maintenanceService;

    private Integer locationId;
    private Path workDir;
    private Process clientProcess;

    @BeforeAll
    void bootRealClient() throws Exception {
        LocationEntity location = this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
        this.locationId = location.getId();
        IssuedTerminalToken token = this.terminalTokenService.createToken(location, "cross-component-e2e");

        this.workDir = Files.createTempDirectory("elwasys-ws-maintenance-e2e");
        Files.createDirectories(this.workDir.resolve("log"));
        Files.writeString(this.workDir.resolve("elwasys.properties"), String.join("\n",
                "backend.url=http://localhost:" + this.port + "/",
                "backend.token=" + token.rawToken(),
                "location=" + location.getName(),
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                // "-dry" (see the ProcessBuilder below) makes ElwaManager/FhemDevicePowerManager
                // skip any real connection attempt to this gateway - a placeholder is enough to
                // satisfy the "some gateway must be configured" startup check. This suite tests
                // the maintenance channel, not the gateway integration (that has its own E2E
                // coverage, see the Client*Deconz/Fhem*E2ETest classes in Client-Raspi).
                "fhem.server=127.0.0.1",
                "fhem.port=1",
                "instance.port=" + freePort(),
                ""));
        Files.writeString(this.workDir.resolve(".client-uid"), "e2e-ws-maintenance-client");

        String clientJar = requireEnv("ELWASYS_TEST_CLIENT_JAR");
        // --module-path/--add-modules: see run-cross-component-e2e.sh for why a plain
        // "java -jar" of this Application subclass needs javafx.graphics resolvable as a
        // named module on a stock JDK (unrelated to the production launch mechanism/Main
        // class, which is untouched).
        String javafxModulePath = requireEnv("ELWASYS_TEST_CLIENT_JAVAFX_MODULE_PATH");
        this.clientProcess = new ProcessBuilder("java", "-Dprism.order=sw",
                "--module-path", javafxModulePath, "--add-modules", "javafx.controls,javafx.fxml,javafx.web",
                "-jar", clientJar, "-dry")
                .directory(this.workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(this.workDir.resolve("client-stdout.log").toFile())
                .start();

        awaitConnected(this.locationId);
    }

    @AfterAll
    void shutdownRealClient() {
        if (this.clientProcess != null) {
            this.clientProcess.destroyForcibly();
        }
    }

    @Test
    @Order(1)
    void thePortalCanQueryTheRealClientsStatus() {
        Map<String, Object> status = this.maintenanceService.requestStatus(this.locationId);

        assertThat(status.get("clientVersion")).as("the real client should report its version").isNotNull()
                .asString().isNotBlank();
        assertThat(status).as("the real client should report its startup time").containsKey("startupTime");
        assertThat(status.get("runningExecutionIds")).as("the running-executions list should be present")
                .isInstanceOf(List.class);
    }

    @Test
    @Order(2)
    void thePortalCanFetchTheRealClientsLog() {
        List<String> lines = this.maintenanceService.requestLog(this.locationId);

        // The real client has been logging its own startup (ElwaManager constructor,
        // connecting to the backend, ...) for several seconds by the time this request is
        // issued - unlike the retired protocol's "must not be null" assertion, we can assert
        // actual content here.
        assertThat(lines).as("the real client's log file should contain its startup log lines").isNotEmpty();
    }

    @Test
    @Order(3)
    void thePortalCanRestartTheRealClient() throws InterruptedException {
        // Does not throw if the real client acknowledged the restart with a RESTART_RESPONSE
        // (see the class Javadoc for the exact scope of what "restart" is verified here).
        this.maintenanceService.requestRestart(this.locationId);

        // The connection must survive the restart (TerminalWebSocketClient#onClose keeps it
        // open for restart=true) - a subsequent request must still reach the same real client.
        assertThat(waitUntil(() -> this.maintenanceService.isConnected(this.locationId), Duration.ofSeconds(15)))
                .as("the terminal connection should still be up after an in-process restart").isTrue();
        List<String> lines = this.maintenanceService.requestLog(this.locationId);
        assertThat(lines).as("the restarted real client should still answer maintenance requests").isNotEmpty();
    }

    // --- helpers --------------------------------------------------------------------------

    private void awaitConnected(Integer locationId) throws InterruptedException {
        assertThat(waitUntil(() -> this.maintenanceService.isConnected(locationId), Duration.ofSeconds(60)))
                .as("the real client process should connect its outgoing WebSocket within 60s").isTrue();
    }

    private static boolean waitUntil(BooleanSupplierEx condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return condition.getAsBoolean();
    }

    @FunctionalInterface
    private interface BooleanSupplierEx {
        boolean getAsBoolean() throws InterruptedException;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " is not set - run this test via "
                    + "Client-Raspi/run-cross-component-e2e.sh, which builds the real client jar and exports it.");
        }
        return value;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
