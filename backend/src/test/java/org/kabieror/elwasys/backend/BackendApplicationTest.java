package org.kabieror.elwasys.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.support.TestPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the full Spring context (incl. the Flyway baseline migration, see
 * db/migration/V1__baseline_schema_0_4_0.sql) against a real PostgreSQL and checks that the app
 * comes up healthy with the expected schema/seed data.
 *
 * <p>Database source: see {@link TestPostgres} - Testcontainers by default, or the local
 * PostgreSQL cluster via {@code ELWASYS_TEST_JDBC_URL} when no Docker daemon is available (this
 * is how backend/run-backend-tests.sh runs it in this environment).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void livenessEndpointReportsUp() {
        // Prozess-Health über die Liveness-Gruppe prüfen (NICHT das Root-/actuator/health): seit
        // AP6 (#32) ziehen die betrieblichen Custom-Indicators das Root-Health je nach DB-Zustand
        // auf OUT_OF_SERVICE (z.B. kein Terminal verbunden). Liveness/Readiness enthalten nur den
        // Prozess-Status und sind daher der deterministische, orchestrierungsrelevante Check.
        ResponseEntity<String> response = this.restTemplate.getForEntity("/actuator/health/liveness", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void flywayMigratedTheBaselineSchema() {
        // Spot-check: core tables + seed data from the baseline migration are present.
        Integer adminUserCount = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'admin'", Integer.class);
        assertThat(adminUserCount).isEqualTo(1);

        String dbVersion = this.jdbcTemplate.queryForObject(
                "SELECT value FROM config WHERE key = 'db.version'", String.class);
        assertThat(dbVersion).isEqualTo("0.4.0");

        Integer successfulFlywayRuns = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(successfulFlywayRuns).isGreaterThanOrEqualTo(1);
    }
}
