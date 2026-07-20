package org.kabieror.elwasys.backend.repository;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroupEntity, Integer> {

    List<UserGroupEntity> findAllByOrderByNameAsc();

    /**
     * Liefert eine andere (beliebige) Benutzergruppe als die übergebene ID - Nachbildung der
     * Alt-Code-Query {@code SELECT id FROM user_groups WHERE id<>? LIMIT 1} aus
     * {@code Common.UserGroup#delete}, siehe {@code UserGroupService#delete}: Ziel für die
     * Umverteilung der Benutzer der gelöschten Gruppe.
     */
    Optional<UserGroupEntity> findFirstByIdNotOrderByIdAsc(Integer id);
}
