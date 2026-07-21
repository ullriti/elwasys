package org.kabieror.elwasys.backend.repository;

import java.util.List;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramRepository extends JpaRepository<ProgramEntity, Integer> {

    /**
     * Alle Programme, alphabetisch nach Name - für die Admin-Programmliste (Portal-UI, siehe
     * kb/03-modules.md, "Portal-UI").
     */
    List<ProgramEntity> findAllByOrderByNameAsc();
}
