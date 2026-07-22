package org.kabieror.elwasys.raspiclient.application;

/**
 * Liefert die Koordinaten des für die Client-Testsuite gemeinsam genutzten Backends (Phase 4
 * AP4, siehe docs/kb/06-ui-tests.md "Testharness"). Der Test-Harness ({@code run-ui-tests.sh}/
 * {@code run-client-e2e.sh}) startet EIN Backend-Jar für den gesamten Testlauf (Flyway
 * migriert dieselbe Test-Datenbank, die auch {@code database-init.sql} initialisiert hat),
 * seedet per {@code token-cli} genau einen Standort-Token für den Standort "Default" und
 * exportiert Basis-URL/Token als Umgebungsvariablen, bevor Maven/Surefire gestartet wird -
 * analog zum bereits bestehenden Muster {@code ELWASYS_TEST_JDBC_URL} im Backend-Modul
 * ({@code backend.support.TestPostgres}).
 * <p>
 * Alle E2E-Testklassen, die den vollen App-Start durchlaufen (Config → Backend-API →
 * Gateway → JavaFX-State-Machine), lesen die Werte hierüber statt sie hart zu kodieren, damit
 * ein einziger geseedeter Token für die ganze Suite ausreicht (alle Testfixtures verwenden
 * denselben Standort "Default", siehe {@code database-init.sql}).
 */
final class TestBackend {

    private static final String URL_ENV = "ELWASYS_TEST_BACKEND_URL";
    private static final String TOKEN_ENV = "ELWASYS_TEST_BACKEND_TOKEN";

    private TestBackend() {
    }

    /**
     * Die Basis-URL des für die Testsuite laufenden Backends, z. B.
     * {@code http://localhost:8099/}. Fällt auf diesen Standard-Wert zurück, wenn die
     * Umgebungsvariable nicht gesetzt ist (z. B. bei einem gezielten
     * {@code mvn test -Dtest=...} ohne den Harness-Wrapper) - die Harness-Skripte setzen sie
     * immer, siehe Klassenkommentar.
     */
    static String url() {
        String url = System.getenv(URL_ENV);
        return url != null && !url.isBlank() ? url : "http://localhost:8099/";
    }

    /**
     * Der geseedete Standort-Token für den Standort "Default". Wirft eine aussagekräftige
     * Ausnahme, wenn er fehlt - ein Test ohne diesen Token kann nicht sinnvoll gegen das
     * Backend laufen.
     */
    static String token() {
        String token = System.getenv(TOKEN_ENV);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Die Umgebungsvariable " + TOKEN_ENV + " ist nicht gesetzt - bitte über " +
                            "run-ui-tests.sh/run-client-e2e.sh starten (siehe docs/kb/06-ui-tests.md), " +
                            "die einen Standort-Token seeden und exportieren.");
        }
        return token;
    }
}
