package org.kabieror.elwasys.backend.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, Integer> {

    /**
     * Lädt eine Ausführung und sperrt ihre Zeile pessimistisch bis zum Transaktionsende
     * ({@code SELECT ... FOR UPDATE}, Issue #20 - AP3). Der Beenden-/Abbruch-Pfad lädt die
     * Ausführung damit FRISCH und GESPERRT innerhalb der Idempotenz-Transaktion, statt den
     * "bereits beendet"-Wächter auf einer zuvor detacht geladenen Instanz zu prüfen: so
     * durchlaufen zwei parallele {@code finish}-Aufrufe nicht beide den
     * {@code finished == false}-Zweig und buchen doppelt ab.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ExecutionEntity e WHERE e.id = :id")
    Optional<ExecutionEntity> findWithLockById(@Param("id") Integer id);

    /**
     * Entspricht der Guthaben-Query in {@code User#loadCredit} im Alt-Code: ALLE noch
     * nicht abgeschlossenen Ausführungen eines Benutzers, UNABHÄNGIG davon, ob sie
     * bereits gestartet wurden ({@code start} kann {@code null} sein!). Bewusst kein
     * {@code start IS NOT NULL}-Filter - siehe {@code CreditService#getCredit} und
     * docs/kb/05-migration-plan.md ("Beobachtungen"): eine gerade erst angelegte, noch nicht
     * gestartete Ausführung mindert das Guthaben bereits um ihren Maximalpreis
     * (Vor-Reservierung gegen Überbuchung).
     */
    List<ExecutionEntity> findByUser_IdAndFinishedFalse(Integer userId);

    /**
     * Entspricht {@code DataManager#getNotFinishedExecutions}: nicht abgeschlossene,
     * aber tatsächlich gestartete Ausführungen eines Benutzers (mit {@code start}-Filter,
     * anders als {@link #findByUser_IdAndFinishedFalse}).
     */
    List<ExecutionEntity> findByUser_IdAndFinishedFalseAndStartIsNotNull(Integer userId);

    /**
     * Entspricht {@code DataManager#getRunningExecution}: laufende Ausführungen eines
     * Geräts (der Alt-Code filtert danach zusätzlich in Java auf {@code !isExpired()} -
     * siehe {@code ExecutionService#getRunningExecution}).
     */
    List<ExecutionEntity> findByDevice_IdAndFinishedFalseAndStartIsNotNull(Integer deviceId);

    /**
     * Entspricht {@code DataManager#getExecutions(Device)}.
     */
    List<ExecutionEntity> findByDevice_IdAndStartIsNotNullOrderByStartDesc(Integer deviceId);

    /**
     * Entspricht {@code DataManager#getLastUser}: die letzte gestartete Ausführung eines
     * Geräts mit einem echten (nicht-virtuellen, id&gt;=0) Benutzer.
     */
    Optional<ExecutionEntity> findFirstByDevice_IdAndUser_IdGreaterThanEqualAndStartIsNotNullOrderByIdDesc(
            Integer deviceId, Integer minUserId);
}
