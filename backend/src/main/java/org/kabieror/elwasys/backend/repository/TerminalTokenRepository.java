package org.kabieror.elwasys.backend.repository;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalTokenRepository extends JpaRepository<TerminalTokenEntity, Integer> {

    Optional<TerminalTokenEntity> findByTokenHash(String tokenHash);

    List<TerminalTokenEntity> findByLocation_IdOrderByCreatedAtDesc(Integer locationId);
}
