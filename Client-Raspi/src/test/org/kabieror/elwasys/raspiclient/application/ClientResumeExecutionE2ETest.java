package org.kabieror.elwasys.raspiclient.application;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.application.fhemsimulator.FhemSimulator;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.testfx.api.FxToolkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client E2E (test plan C13): an execution that was already running when the
 * client starts (e.g. after a crash/restart) is resumed. The client scans the
 * database for unfinished, non-expired executions on boot
 * (ElwaManager.initiate) and re-registers them with the ExecutionManager.
 *
 * We seed a running execution (start set, finished=false) before launching the
 * app and assert the ExecutionManager picks it up as a running execution.
 *
 * Run: ./run-client-e2e.sh   (or xvfb-run mvn test -Dtest=ClientResumeExecutionE2ETest)
 * See docs/kb/08-test-plan.md.
 */
public class ClientResumeExecutionE2ETest {

    private static final int FHEM_PORT = 7081;
    private static final String DEVICE_NAME = "E2E-WM-Resume";
    private static final String PROGRAM_NAME = "E2E-Resume-Prog";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/elwasys";

    private static FhemSimulator fhem;
    private static Path workDir;

    @BeforeAll
    static void seedAndLaunch() throws Exception {
        seedFixtures();

        fhem = new FhemSimulator();
        fhem.start(FHEM_PORT);

        workDir = Files.createTempDirectory("elwasys-resume-e2e");
        Files.writeString(workDir.resolve("elwasys.properties"), String.join("\n",
                "backend.url=" + TestBackend.url(),
                "backend.token=" + TestBackend.token(),
                "location=Default",
                "displayTimeout=-1",
                "startupDelay=0",
                "sessionTimeout=600",
                "portalUrl=",
                "fhem.server=localhost",
                "fhem.port=" + FHEM_PORT,
                "instance.port=8280",
                "maintenance.server=localhost",
                "maintenance.port=3600",
                ""));
        Files.writeString(workDir.resolve(".client-uid"), "e2e-resume-client");
        System.setProperty("user.dir", workDir.toString());

        FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication(Main.class);
        waitForState(MainFormState.SELECT_DEVICE, Duration.ofSeconds(45));
    }

    @AfterAll
    static void shutdown() {
        try {
            FxToolkit.cleanupStages();
        } catch (Exception ignored) {
            // best effort
        }
        if (fhem != null) {
            fhem.stop();
        }
        cleanupFixtures();
    }

    /**
     * Phase 4 CI-Stabilität (siehe docs/kb/05-migration-plan.md, Änderungslog "Phase 4
     * CI-Stabilität (deCONZ)"): dieser Test seedet absichtlich eine DAUERHAFT unfertige
     * Ausführung ({@code finished=FALSE}, siehe {@link #seedFixtures()}), um den
     * Wiederaufnahme-Pfad (Testfall C13) zu prüfen - ohne Aufräumen überlebt dieser
     * Datensatz das Testende. {@code ElwaManager#initiate()} scannt beim Start ALLE Geräte
     * am Standort (nicht nur die des jeweils eigenen Tests) nach unfertigen Ausführungen,
     * um sie automatisch fortzusetzen (dasselbe Testfall-C13-Verhalten, das dieser Test
     * selbst prüft) - eine JEDE SPÄTER in DERSELBEN {@code mvn test}-Ausführung startende
     * Testklasse würde diesen Datensatz also ebenfalls "wiederaufnehmen" wollen. Andere
     * Testklassen räumen ihre eigenen Fixtures nur beim NÄCHSTEN eigenen Lauf auf (per
     * {@code LIKE}-Muster im jeweiligen {@code seedFixtures()}), decken dieses Gerät aber
     * nicht zwingend ab - insbesondere die drei {@code *Deconz*}-Testklassen, deren
     * engeres {@code LIKE 'E2E-Deconz-%'}-Muster diesen Gerätenamen nicht trifft. Da
     * Surefire die Klassenreihenfolge NICHT alphabetisch, sondern dateisystemabhängig
     * wählt (lokal reproduzierbar anders als in CI - siehe Änderungslog), konnte dieser
     * Datensatz in CI (aber nie lokal) direkt vor {@code ClientAutoEndDeconzE2ETest} zu
     * liegen kommen: dessen Start-Restore-Schleife versuchte daraufhin, dieses (rein
     * fhem-basierte, {@code deconz_uuid=''}) Gerät über den für DIESEN Client aktiven
     * deCONZ-Gateway einzuschalten - eine leere deCONZ-Id lässt sich auf keinen
     * Simulator-Pfad routen (404), was nach den konfigurierten Wiederholungen die gesamte
     * Initialisierung von {@code ClientAutoEndDeconzE2ETest} zum Absturz brachte, obwohl
     * der eigentliche Fehler gar nichts mit dessen eigenen Fixtures zu tun hatte. Fix: die
     * einzige Testklasse im Baum, die bewusst eine dauerhaft unfertige Ausführung seedet,
     * räumt sie jetzt selbst auf, statt sich auf eine zufällige spätere Testklasse mit
     * passendem {@code LIKE}-Muster zu verlassen.
     */
    private static void cleanupFixtures() {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
                Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN "
                    + "(SELECT id FROM executions WHERE device_id IN "
                    + "(SELECT id FROM devices WHERE name = '" + DEVICE_NAME + "'))");
            s.executeUpdate(
                    "DELETE FROM executions WHERE device_id IN (SELECT id FROM devices WHERE name = '" + DEVICE_NAME
                            + "')");
            s.executeUpdate("DELETE FROM devices WHERE name = '" + DEVICE_NAME + "'");
            s.executeUpdate("DELETE FROM programs WHERE name = '" + PROGRAM_NAME + "'");
        } catch (Exception e) {
            // Best effort: ein nicht aufgeräumtes Fixture ist ein Test-Hygiene-Problem,
            // aber kein Grund, die (bereits erfolgreich geprüften) Assertions dieses
            // Tests nachträglich scheitern zu lassen.
        }
    }

    @Test
    void an_interrupted_execution_is_resumed_on_startup() throws Exception {
        // The execution seeded as "running" before the app booted must have been
        // picked up by the ExecutionManager during startup.
        assertTrue(waitUntil(() -> runningExecutionOnSeededDevice() != null, Duration.ofSeconds(15)),
                "The interrupted execution should be resumed as a running execution");

        final ClientExecution resumed = runningExecutionOnSeededDevice();
        assertTrue(resumed != null && resumed.isRunning(),
                "The resumed execution should report itself as running");
    }

    // --- helpers ------------------------------------------------------------

    private static ClientExecution runningExecutionOnSeededDevice() {
        for (final ClientExecution e : ElwaManager.instance.getExecutionManager().getRunningExecutions()) {
            if (DEVICE_NAME.equals(e.getDevice().getName())) {
                return e;
            }
        }
        return null;
    }

    private static void seedFixtures() throws Exception {
        try (Connection c = DriverManager.getConnection(DB_URL, "postgres", "postgres");
             Statement s = c.createStatement()) {
            final int locationId = queryInt(s, "SELECT id FROM locations WHERE name='Default'");
            final int groupId = queryInt(s, "SELECT id FROM user_groups ORDER BY id LIMIT 1");

            s.executeUpdate("DELETE FROM credit_accounting WHERE execution_id IN " +
                    "(SELECT id FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%'))");
            s.executeUpdate("DELETE FROM executions WHERE device_id IN " +
                    "(SELECT id FROM devices WHERE name LIKE 'E2E-%')");
            s.executeUpdate("DELETE FROM devices WHERE name LIKE 'E2E-%'");
            s.executeUpdate("DELETE FROM programs WHERE name LIKE 'E2E-%'");

            // A FIXED program without auto-end so the resumed execution is not
            // ended by the (zero-power) simulator during the assertion window.
            final int programId = insertReturningId(s,
                    "INSERT INTO programs (name, type, max_duration, free_duration, flagfall, rate, " +
                            "time_unit, auto_end, earliest_auto_end, enabled) VALUES ('" + PROGRAM_NAME +
                            "', 'FIXED', 3600, 0, 1.50, NULL, NULL, FALSE, 0, TRUE) RETURNING id");
            final int deviceId = insertReturningId(s,
                    "INSERT INTO devices (name, position, location_id, fhem_name, fhem_switch_name, " +
                            "fhem_power_name, deconz_uuid, auto_end_power_threshold, auto_end_wait_time, enabled) " +
                            "VALUES ('" + DEVICE_NAME + "', 1, " + locationId +
                            ", 'wm1', 'wm1sw', 'wm1pw', '', 0.5, 20, TRUE) RETURNING id");
            s.executeUpdate("INSERT INTO device_program_rel (device_id, program_id) VALUES ("
                    + deviceId + ", " + programId + ")");
            s.executeUpdate("INSERT INTO programs_valid_user_groups (program_id, group_id) VALUES ("
                    + programId + ", " + groupId + ")");
            s.executeUpdate("INSERT INTO devices_valid_user_groups (device_id, group_id) VALUES ("
                    + deviceId + ", " + groupId + ")");

            final int userId = insertReturningId(s,
                    "INSERT INTO users (name, username, card_ids, group_id, is_admin, blocked, deleted) VALUES ("
                            + "'E2E Resume', 'e2e_resume_" + System.currentTimeMillis() + "', '1"
                            + String.format("%08d", System.currentTimeMillis() % 100_000_000L) + "', "
                            + groupId + ", FALSE, FALSE, FALSE) RETURNING id");
            s.executeUpdate("INSERT INTO credit_accounting (user_id, amount, description) VALUES ("
                    + userId + ", 100, 'E2E seed')");

            // Seed the running execution: started just now, not finished.
            s.executeUpdate("INSERT INTO executions (device_id, program_id, user_id, start, finished) VALUES ("
                    + deviceId + ", " + programId + ", " + userId + ", NOW(), FALSE)");
        }
    }

    private static int queryInt(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static int insertReturningId(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getInt(1);
        }
    }

    private static void waitForState(MainFormState target, Duration timeout) throws InterruptedException {
        assertTrue(waitUntil(() -> {
            final var c = ElwaManager.instance.getMainFormController();
            return c != null && c.getMainFormState() == target;
        }, timeout), "Expected to reach state " + target);
    }

    private static boolean waitUntil(BooleanSupplierEx condition, Duration timeout) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return condition.getAsBoolean();
    }

    @FunctionalInterface
    private interface BooleanSupplierEx {
        boolean getAsBoolean();
    }
}
