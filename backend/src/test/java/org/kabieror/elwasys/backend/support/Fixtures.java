package org.kabieror.elwasys.backend.support;

import java.util.UUID;

/**
 * Kleine Test-Helfer für eindeutige Namen (siehe {@link AbstractBackendIT}: Testdaten
 * werden committet, nicht zurückgerollt, daher müssen Namen pro Testlauf eindeutig sein).
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Eindeutige, formal gültige Kartennummer (hexadezimal, siehe
     * {@code CardLoginRequest}-Validierung, Issue #21) - ohne Bindestriche/Buchstaben
     * außerhalb {@code [0-9A-Fa-f]}, damit sie die strenge API-Validierung passiert.
     */
    public static String uniqueCardId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
