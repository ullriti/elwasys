package org.kabieror.elwasys.backend.repository;

import java.util.List;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Integer> {

    /**
     * Entspricht {@code DataManager#getDevicesToDisplay}: alle Geräte eines Standorts,
     * alphabetisch nach Name (Alt-Code: {@code ORDER BY name}).
     */
    List<DeviceEntity> findByLocation_IdOrderByName(Integer locationId);
}
