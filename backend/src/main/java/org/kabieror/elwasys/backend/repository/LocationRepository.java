package org.kabieror.elwasys.backend.repository;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<LocationEntity, Integer> {

    List<LocationEntity> findAllByOrderByNameAsc();

    Optional<LocationEntity> findByName(String name);
}
