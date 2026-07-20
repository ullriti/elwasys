package org.kabieror.elwasys.backend.repository;

import java.util.List;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroupEntity, Integer> {

    List<UserGroupEntity> findAllByOrderByNameAsc();
}
