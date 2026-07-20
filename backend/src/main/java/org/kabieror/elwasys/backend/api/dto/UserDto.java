package org.kabieror.elwasys.backend.api.dto;

import java.math.BigDecimal;
import org.kabieror.elwasys.backend.domain.UserEntity;

/**
 * DTO statt Entity an der API-Grenze (siehe kb/05-migration-plan.md, AP4: "beachte den
 * EAGER-Hinweis aus AP2 - gezielte Abfragen/DTO-Mapping, keine Entity-Serialisierung").
 * Enthält bewusst KEIN Passwort-/Sicherheitsfeld.
 */
public record UserDto(Integer id, String name, String username, String email, boolean admin, boolean blocked,
        Integer groupId, String groupName, BigDecimal credit) {

    public static UserDto of(UserEntity user, BigDecimal credit) {
        return new UserDto(user.getId(), user.getName(), user.getUsername(), user.getEmail(), user.isAdmin(),
                user.isBlocked(), user.getGroup().getId(), user.getGroup().getName(), credit);
    }
}
