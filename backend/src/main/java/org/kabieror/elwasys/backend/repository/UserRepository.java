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
     * Zeilenumbruch getrennten) Kartennummern in {@code card_ids}.
     *
     * <p><b>Regex-frei (Issue #21, Pre-Launch AP4):</b> die frühere Variante bettete den
     * Parameter in einen Postgres-Regex ein ({@code card_ids ~ '(?n)^' || :cardId || '$'}).
     * Weil {@code :cardId} dabei als Regex-Muster interpretiert wurde, meldete ein als
     * Kartennummer übergebenes Metazeichen-Muster (z.B. {@code .*}) einen beliebigen Benutzer
     * an – eine Regex-Injection. Diese Query zerlegt {@code card_ids} stattdessen an den
     * Zeilenumbrüchen in ein Array und prüft exakte Zeilen-Gleichheit
     * ({@code :cardId = ANY(string_to_array(card_ids, E'\n'))}). Das Matching-Verhalten
     * bleibt fachlich identisch (exakter Treffer genau EINER Kartennummer, kein
     * Teilstring-/Präfix-Treffer), interpretiert die Eingabe aber ausschließlich als
     * Literal.
     */
    @Query(value = "SELECT * FROM users WHERE deleted = FALSE "
            + "AND :cardId = ANY(string_to_array(card_ids, E'\n')) LIMIT 1", nativeQuery = true)
    Optional<UserEntity> findByCardId(@Param("cardId") String cardId);

    Optional<UserEntity> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    /**
     * Prüft, ob ein nicht gelöschter Benutzer mit diesem Benutzernamen (case-insensitiv)
     * existiert – Grundlage des Service-seitigen Eindeutigkeits-Guards
     * ({@code UserService#create}/{@code #update}, Issue #23, Pre-Launch AP4). Verhindert, dass
     * zwei nur in der Groß-/Kleinschreibung abweichende Benutzernamen entstehen, an denen der
     * case-insensitive Login später mit {@code IncorrectResultSizeDataAccessException}
     * scheitern würde.
     */
    boolean existsByUsernameIgnoreCaseAndDeletedFalse(String username);

    /**
     * Entspricht {@code DataManager#getUserByEmail} (Alt-Portal
     * {@code PasswordForgotWindow}, Testfall P19): Suche eines nicht gelöschten Benutzers per
     * Email-Adresse, case-insensitiv (E-Mail-Adressen werden allgemein als
     * case-insensitiv im lokalen Teil/Domain-Teil behandelt - der Alt-Code selbst vergleicht
     * SQL-seitig ohnehin ohne explizite Case-Sensitivität, siehe {@code DataManager}).
     *
     * <p><b>Liste statt {@code Optional} (Issue #47, Pre-Launch AP4):</b> {@code users.email}
     * trägt KEINE Eindeutigkeits-Constraint – mehrere Benutzer dürfen dieselbe Adresse haben
     * (z.B. ein Elternteil für Kinder-Konten). Ein {@code Optional}-Rückgabewert scheiterte in
     * diesem Fall mit {@code IncorrectResultSizeDataAccessException}. Der Passwort-Reset
     * ({@code PasswordResetService#requestReset}) verarbeitet daher bewusst ALLE Treffer.
     */
    List<UserEntity> findByEmailIgnoreCaseAndDeletedFalse(String email);

    /**
     * Für die öffentliche Passwort-Reset-Ansicht ({@code ResetPasswordView}, siehe
     * {@code PasswordResetService#resetPassword}): findet den Benutzer, dem gerade ein
     * bestimmter Reset-Schlüssel zugeordnet ist (Spalte {@code password_reset_key}, seit der
     * Alt-Bestandsdatenbank vorhanden - siehe {@code common.User#generatePasswordResetKey}/
     * {@code #passwordResetKeyIsValid}, hier wiederverwendet statt einer neuen Spalte/Tabelle).
     */
    Optional<UserEntity> findByPasswordResetKeyAndDeletedFalse(String passwordResetKey);
}
