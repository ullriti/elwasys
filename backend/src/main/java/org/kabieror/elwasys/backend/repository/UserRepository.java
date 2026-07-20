package org.kabieror.elwasys.backend.repository;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

    /**
     * Entspricht {@code DataManager#getUsers}: alle nicht gelöschten Benutzer.
     */
    List<UserEntity> findByDeletedFalse();

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
}
