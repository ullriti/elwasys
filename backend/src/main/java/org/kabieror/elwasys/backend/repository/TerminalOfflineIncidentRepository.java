package org.kabieror.elwasys.backend.repository;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.TerminalOfflineIncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Zugriff auf die vom Terminal gemeldeten Offline-Vorfälle (Issue #89, siehe
 * {@link TerminalOfflineIncidentEntity}).
 */
public interface TerminalOfflineIncidentRepository extends JpaRepository<TerminalOfflineIncidentEntity, Long> {

    /** Idempotenz-Anker: verhindert Doppel-Einträge bei erneut gemeldeten Vorfällen. */
    Optional<TerminalOfflineIncidentEntity> findByIncidentKey(String incidentKey);

    /**
     * Offene (noch nicht quittierte) Vorfälle, neueste zuerst - Grundlage für den
     * Health-Indicator und die Portal-Ansicht.
     */
    List<TerminalOfflineIncidentEntity> findByAcknowledgedAtIsNullOrderByReportedAtDesc();

    /** Anzahl offener Vorfälle - der Health-Indicator braucht nur die Zahl, nicht die Entities. */
    long countByAcknowledgedAtIsNull();
}
