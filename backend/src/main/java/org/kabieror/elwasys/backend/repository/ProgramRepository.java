package org.kabieror.elwasys.backend.repository;

import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<ProgramEntity, Integer> {
}
