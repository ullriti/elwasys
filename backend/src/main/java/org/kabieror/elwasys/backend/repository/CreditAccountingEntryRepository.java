package org.kabieror.elwasys.backend.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditAccountingEntryRepository extends JpaRepository<CreditAccountingEntryEntity, Integer> {

    /**
     * Entspricht {@code DataManager#getAccountingEntries}.
     */
    List<CreditAccountingEntryEntity> findByUser_IdOrderByDateDesc(Integer userId);

    /**
     * Entspricht der Summenbildung in {@code User#loadCredit}
     * ({@code SELECT SUM(amount) AS credit FROM credit_accounting WHERE user_id=...}).
     * {@code COALESCE} bildet das Alt-Code-Verhalten nach, dass ein Benutzer ohne
     * Buchungen {@code 0.00} statt {@code NULL} als Summe erhält (dort wird
     * {@code res.getBigDecimal(...) == null} auf {@code new BigDecimal("0.00")}
     * abgebildet - siehe {@code CreditService#getCredit}).
     */
    @Query("SELECT COALESCE(SUM(c.amount), 0.00) FROM CreditAccountingEntryEntity c WHERE c.user.id = :userId")
    BigDecimal sumAmountByUserId(@Param("userId") Integer userId);

    /**
     * Entspricht {@code DataManager#getLastInpayment}: letzte positive Buchung
     * ({@code amount > 0}) eines Benutzers.
     */
    Optional<CreditAccountingEntryEntity> findFirstByUser_IdAndAmountGreaterThanOrderByDateDesc(Integer userId,
            BigDecimal amountThreshold);
}
