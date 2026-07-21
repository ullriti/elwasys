package org.kabieror.elwasys.backend.repository;

import java.util.List;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Integer> {

    /**
     * Entspricht {@code DataManager#getDevicesToDisplay}: alle Geräte eines Standorts,
     * alphabetisch nach Name (Alt-Code: {@code ORDER BY name}). Wird außerdem als
     * Lösch-Wächter für Standorte verwendet (siehe {@code LocationService#delete}: ein
     * Standort mit noch zugeordneten Geräten kann nicht gelöscht werden, analog zur
     * fehlenden {@code ON DELETE}-Klausel auf {@code devices.location_id}, siehe
     * kb/02-data-model.md).
     */
    List<DeviceEntity> findByLocation_IdOrderByName(Integer locationId);

    /**
     * Alle Geräte, alphabetisch nach Name - für die Admin-Geräteliste (Portal-UI, siehe
     * kb/03-modules.md, "Portal-UI").
     */
    List<DeviceEntity> findAllByOrderByNameAsc();

    /**
     * Alle Geräte, denen das gegebene Programm zugeordnet ist - Lösch-Wächter für Programme
     * (siehe {@code ProgramService#delete}, Nachbildung von
     * {@code Portal/.../views/ProgramsView#deleteProgram}: ein Programm, das noch auf
     * mindestens einem Gerät verfügbar ist, kann nicht gelöscht werden).
     */
    List<DeviceEntity> findByPrograms_Id(Integer programId);
}
