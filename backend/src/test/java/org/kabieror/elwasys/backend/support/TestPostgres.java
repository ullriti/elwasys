package org.kabieror.elwasys.backend.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Bootstraps the PostgreSQL instance used by the backend integration tests.
 *
 * <p>Default: Testcontainers spins up a throwaway PostgreSQL container. This works wherever a
 * Docker daemon is available (e.g. GitHub Actions CI, see .github/workflows/ci.yml).
 *
 * <p>Override: if {@code ELWASYS_TEST_JDBC_URL} (system property or environment variable) is
 * set, that database is used instead of Testcontainers, and Testcontainers/Docker is never
 * touched. This is how the tests run in environments without a Docker daemon - see
 * backend/run-backend-tests.sh, which points this at the local PostgreSQL cluster (the same
 * pattern Client-Raspi/run-ui-tests.sh uses for its own DB-backed tests).
 * {@code ELWASYS_TEST_DB_USER}/{@code ELWASYS_TEST_DB_PASSWORD} (system property or environment
 * variable) configure the credentials for the override; they default to postgres/postgres.
 */
public final class TestPostgres {

    private static volatile String jdbcUrl;
    private static volatile String username;
    private static volatile String password;

    private TestPostgres() {
    }

    public static String jdbcUrl() {
        ensureStarted();
        return jdbcUrl;
    }

    public static String username() {
        ensureStarted();
        return username;
    }

    public static String password() {
        ensureStarted();
        return password;
    }

    private static synchronized void ensureStarted() {
        if (jdbcUrl != null) {
            return;
        }

        String overrideUrl = firstNonBlank(System.getProperty("ELWASYS_TEST_JDBC_URL"),
                System.getenv("ELWASYS_TEST_JDBC_URL"));
        if (overrideUrl != null) {
            jdbcUrl = overrideUrl;
            username = firstNonBlank(System.getProperty("ELWASYS_TEST_DB_USER"),
                    System.getenv("ELWASYS_TEST_DB_USER"), "postgres");
            password = firstNonBlank(System.getProperty("ELWASYS_TEST_DB_PASSWORD"),
                    System.getenv("ELWASYS_TEST_DB_PASSWORD"), "postgres");
            return;
        }

        // No override configured: fall back to Testcontainers (requires a Docker daemon).
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("elwasys_backend_it");
        container.start();
        jdbcUrl = container.getJdbcUrl();
        username = container.getUsername();
        password = container.getPassword();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
