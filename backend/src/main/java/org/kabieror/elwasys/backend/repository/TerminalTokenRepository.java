package org.kabieror.elwasys.backend.repository;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalTokenRepository extends JpaRepository<TerminalTokenEntity, Integer> {

    Optional<TerminalTokenEntity> findByTokenHash(String tokenHash);

    /**
     * Die Tokens eines Standorts, neueste zuerst. Der Zweitschlüssel {@code id DESC} ist nicht
     * kosmetisch: {@code created_at} kommt aus {@code LocalDateTime.now()} beim Anlegen der
     * Entität und hat damit die Auflösung der Systemuhr - zwei unmittelbar nacheinander erzeugte
     * Tokens (im Portal ein Klick nach dem anderen) können denselben Zeitstempel tragen, und die
     * Reihenfolge gleicher Werte ist in SQL sonst unbestimmt.
     */
    List<TerminalTokenEntity> findByLocation_IdOrderByCreatedAtDescIdDesc(Integer locationId);
}
