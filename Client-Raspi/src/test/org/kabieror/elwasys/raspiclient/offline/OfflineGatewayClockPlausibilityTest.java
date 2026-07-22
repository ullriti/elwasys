package org.kabieror.elwasys.raspiclient.offline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import org.kabieror.elwasys.raspiclient.api.ApiClient;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotDto;

/**
 * Plausibilitätscheck der lokalen Terminaluhr gegen den Snapshot-Zeitpunkt (Issue #54). Ein
 * Raspberry Pi hat keine RTC; nach einem Stromausfall ohne Netz kann die Uhr falsch stehen.
 * Liegt die Terminalzeit VOR dem Snapshot-Zeitpunkt, muss der Snapshot als unbrauchbar gelten,
 * damit nicht auf Basis einer falschen Uhr falsch abgerechnet wird.
 */
class OfflineGatewayClockPlausibilityTest {

    private static SnapshotDto snapshotGeneratedAt(LocalDateTime generatedAt) {
        return new SnapshotDto(1, "Waschkeller", generatedAt, 60, List.of(), List.of(), List.of(), List.of());
    }

    private OfflineGateway gatewayWith(Path dir, SnapshotDto snapshot) {
        OfflineSnapshotStore store = new OfflineSnapshotStore(dir.resolve("snapshot.json"));
        store.save(snapshot);
        return new OfflineGateway(new ApiClient("http://localhost:1/", "test-token"), store,
                new OfflineJournal(dir.resolve("offline-journal.jsonl")));
    }

    @Test
    void aSnapshotFromTheFutureIsRejectedAsUnusable(@TempDir Path dir) {
        // Terminalzeit steht offensichtlich vor dem Snapshot-Zeitpunkt -> Uhr falsch gestellt.
        OfflineGateway gateway = gatewayWith(dir, snapshotGeneratedAt(LocalDateTime.now().plusDays(1)));

        assertFalse(gateway.hasUsableSnapshot(), "a snapshot from the future must not be used");

        ApiException original = new ApiException("Backend nicht erreichbar");
        ApiException thrown = assertThrows(ApiException.class, () -> gateway.cardLogin("card-1", original));
        assertSame(original, thrown,
                "with an implausible clock the offline path rethrows the original communication failure");
    }

    @Test
    void aFreshSnapshotWithinItsWindowIsUsable(@TempDir Path dir) {
        OfflineGateway gateway = gatewayWith(dir, snapshotGeneratedAt(LocalDateTime.now().minusMinutes(10)));

        assertTrue(gateway.hasUsableSnapshot(), "a recent snapshot within its offline window stays usable");
    }
}
