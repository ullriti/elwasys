package org.kabieror.elwasys.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.TerminalIdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TerminalIdempotencyKeyRepository extends JpaRepository<TerminalIdempotencyKeyEntity, Long> {

    Optional<TerminalIdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Löscht alle Idempotenz-Schlüssel, deren {@code created_at} vor dem Schwellwert liegt
     * (Retention/Purge, Issue #32 - Betriebskonzept Dauerbetrieb). Die Tabelle wächst im
     * Dauerbetrieb sonst unbegrenzt (ein Eintrag je terminal-gemeldetem Execution-Ereignis,
     * siehe {@code V4__create_terminal_idempotency_keys.sql}) - siehe
     * {@link org.kabieror.elwasys.backend.service.IdempotencyKeyRetentionScheduler}.
     *
     * <p>Gefahrlos, weil ein Replay maximal so alt wie das Offline-Journal des Terminals sein
     * kann (Nachmeldung offline getätigter Buchungen) - deutlich unter der Default-
     * Aufbewahrung. Bewusst als Bulk-{@code DELETE} per JPQL statt Laden+Löschen der Entities,
     * damit der Aufräum-Job auch bei Jahren an Bestand keine Entity-Fluten in den
     * Persistenzkontext zieht.
     *
     * @return Anzahl der gelöschten Zeilen
     */
    @Modifying
    @Query("DELETE FROM TerminalIdempotencyKeyEntity e WHERE e.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
