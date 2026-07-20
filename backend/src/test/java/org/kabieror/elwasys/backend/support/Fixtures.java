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
}
