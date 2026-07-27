package org.kabieror.elwasys.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regressionstest zur Umstellung von {@link Utilities#APP_VERSION} auf die Jar-Manifest-Version
 * (GitVersion/Release-Umbau): Vorher war die Version eine feste Quelltext-Konstante, die nur der
 * Release-Workflow per {@code sed} ersetzte. Jetzt kommt sie aus {@code Implementation-Version}
 * im Manifest – der Sentinel bleibt der Fallback für Läufe ohne gebautes Jar (IDE/Tests).
 *
 * Die Manifest-Seite selbst (Maven schreibt die POM-Version wirklich hinein) prüft der
 * Release-Workflow direkt am gebauten fat-jar, siehe {@code .github/workflows/release.yml}.
 */
class UtilitiesAppVersionTest {

    @Test
    void manifest_version_wins_when_present() {
        assertEquals("1.4.2", Utilities.resolveAppVersion("1.4.2"));
        assertEquals("1.5.0-rc.7", Utilities.resolveAppVersion("1.5.0-rc.7"));
    }

    @Test
    void falls_back_to_the_sentinel_without_a_jar_manifest() {
        // Ohne Jar (IDE, target/classes, Surefire) ist die Manifest-Version nicht verfügbar.
        assertEquals(Utilities.LOCAL_DEVELOPMENT_VERSION, Utilities.resolveAppVersion(null));
        assertEquals(Utilities.LOCAL_DEVELOPMENT_VERSION, Utilities.resolveAppVersion(""));
        assertEquals(Utilities.LOCAL_DEVELOPMENT_VERSION, Utilities.resolveAppVersion("   "));
    }

    /**
     * APP_VERSION geht in den WebSocket-Hello (clientVersion), in den Readiness-Marker und ins
     * Log. Ein {@code null} dort wäre ein stiller Ausfall dieser Betriebsinformationen, deshalb
     * ist die Nicht-Leere hier festgeschrieben.
     */
    @Test
    void app_version_is_always_a_usable_string() {
        assertNotNull(Utilities.APP_VERSION);
        assertFalse(Utilities.APP_VERSION.isBlank());
    }
}
