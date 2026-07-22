package org.kabieror.elwasys.backend.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    /**
     * Entspricht {@code DataManager#getUsers}: alle nicht gelöschten Benutzer.
     */
    List<UserEntity> findByDeletedFalse();

    /**
     * Lädt einen Benutzer und sperrt seine Zeile pessimistisch bis zum Transaktionsende
     * ({@code SELECT ... FOR UPDATE}, Issue #20 - AP3). Serialisiert alle Guthaben-relevanten
     * Read-then-Write-Sequenzen dieses Benutzers (Start-Guthabencheck, {@code payout},
     * {@code payExecution}), damit zwei parallele Buchungen nicht beide denselben Ausgangsstand
     * lesen und das Guthaben negativ werden lassen. MUSS innerhalb der Transaktion aufgerufen
     * werden, in der anschließend geprüft und gebucht wird (siehe {@code CreditService}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findWithLockById(@Param("id") Integer id);

    /**
     * Entspricht {@code DataManager#getUserByCardId}: Suche über eine der (durch
     * Zeilenumbruch getrennten) Kartennummern in {@code card_ids} per Postgres-Regex
     * {@code (?n)^cardId$} - "(?n)" = zeilenweise (newline-sensitive) Suche, damit
     * {@code ^}/{@code $} an jedem Zeilenumbruch greifen, nicht nur am Gesamtanfang/-ende
     * (1:1 wie im Alt-Code, nur mit gebundenem statt String-verkettetem Parameter -
     * gleiches Matching-Verhalten, ohne SQL-Injection-Risiko).
     */
    @Query(value = "SELECT * FROM users WHERE deleted = FALSE AND card_ids ~ ('(?n)^' || :cardId || '$') LIMIT 1",
            nativeQuery = true)
    Optional<UserEntity> findByCardId(@Param("cardId") String cardId);

    Optional<UserEntity> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    /**
     * Entspricht {@code DataManager#getUserByEmail} (Alt-Portal
     * {@code PasswordForgotWindow}, Testfall P19): Suche eines nicht gelöschten Benutzers per
     * Email-Adresse, case-insensitiv (E-Mail-Adressen werden allgemein als
     * case-insensitiv im lokalen Teil/Domain-Teil behandelt - der Alt-Code selbst vergleicht
     * SQL-seitig ohnehin ohne explizite Case-Sensitivität, siehe {@code DataManager}).
     */
    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedFalse(String email);

    /**
     * Für die öffentliche Passwort-Reset-Ansicht ({@code ResetPasswordView}, siehe
     * {@code PasswordResetService#resetPassword}): findet den Benutzer, dem gerade ein
     * bestimmter Reset-Schlüssel zugeordnet ist (Spalte {@code password_reset_key}, seit der
     * Alt-Bestandsdatenbank vorhanden - siehe {@code common.User#generatePasswordResetKey}/
     * {@code #passwordResetKeyIsValid}, hier wiederverwendet statt einer neuen Spalte/Tabelle).
     */
    Optional<UserEntity> findByPasswordResetKeyAndDeletedFalse(String passwordResetKey);
}
