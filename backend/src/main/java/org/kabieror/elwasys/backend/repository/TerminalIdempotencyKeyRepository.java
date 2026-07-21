package org.kabieror.elwasys.backend.repository;

import java.util.Optional;
import org.kabieror.elwasys.backend.domain.TerminalIdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerminalIdempotencyKeyRepository extends JpaRepository<TerminalIdempotencyKeyEntity, Long> {

    Optional<TerminalIdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);
}
