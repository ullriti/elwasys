package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.DuplicateCardIdException;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tests für {@link UserService} (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/UserWindow} (Testfälle P6/P7), ohne den
 * Admin-Passwort-Reset-Teil (AP4).
 */
class UserServiceTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    private UserGroupEntity group() {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
    }

    @Test
    void createsAndUpdatesAUser() {
        UserGroupEntity group = group();
        String cardId = Fixtures.unique("CARD");

        UserEntity created = this.userService.create("Max Mustermann", Fixtures.unique("max"), "max@example.com",
                new String[] {cardId}, false, group);

        assertThat(created.getId()).isNotNull();
        assertThat(created.isBlocked()).isFalse();
        assertThat(created.getCardIds()).containsExactly(cardId);

        UserGroupEntity otherGroup = group();
        UserEntity updated = this.userService.update(created, "Erika Mustermann", created.getUsername(),
                "erika@example.com", new String[] {cardId}, true, otherGroup);

        assertThat(updated.getName()).isEqualTo("Erika Mustermann");
        assertThat(updated.isBlocked()).as("P7: Benutzer sperren").isTrue();
        assertThat(updated.getGroup()).isEqualTo(otherGroup);
    }

    @Test
    void usernameIsStoredLowerCase() {
        UserGroupEntity group = group();
        String mixedCaseUsername = Fixtures.unique("MixedCase");

        UserEntity created = this.userService.create("Some User", mixedCaseUsername, null, new String[0], false,
                group);

        assertThat(created.getUsername()).isEqualTo(mixedCaseUsername.toLowerCase());
    }

    @Test
    void creatingAUserWithACardIdAlreadyAssignedToAnotherUserFails() {
        UserGroupEntity group = group();
        String cardId = Fixtures.unique("CARD");
        this.userService.create("First Owner", Fixtures.unique("first"), null, new String[] {cardId}, false, group);

        assertThatThrownBy(
                () -> this.userService.create("Second Owner", Fixtures.unique("second"), null, new String[] {cardId},
                        false, group)).isInstanceOf(DuplicateCardIdException.class)
                .hasMessageContaining("First Owner");
    }

    @Test
    void updatingAUserWithItsOwnCardIdDoesNotFail() {
        UserGroupEntity group = group();
        String cardId = Fixtures.unique("CARD");
        UserEntity user = this.userService.create("Owner", Fixtures.unique("owner"), null, new String[] {cardId},
                false, group);

        // Muss klaglos funktionieren - die Karte gehört ja bereits dem bearbeiteten Benutzer.
        UserEntity updated = this.userService.update(user, "Owner Renamed", user.getUsername(), null,
                new String[] {cardId}, false, group);

        assertThat(updated.getCardIds()).containsExactly(cardId);
    }

    @Test
    void deletingAUserSoftDeletesAndPrefixesTheUsernameToFreeItUp() {
        UserGroupEntity group = group();
        String username = Fixtures.unique("todelete");
        UserEntity user = this.userService.create("To Delete", username, null, new String[0], false, group);
        Integer id = user.getId();

        this.userService.delete(user);

        UserEntity reloaded = this.userRepository.findById(id).orElseThrow();
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getUsername()).isEqualTo("#del" + id + "#" + username);
        assertThat(this.userService.findAllActive()).doesNotContain(reloaded);

        // Der ursprüngliche Benutzername ist jetzt wieder frei - ein neuer Benutzer kann ihn
        // verwenden (UNIQUE-Constraint auf users.username, siehe docs/kb/02-data-model.md).
        UserEntity newUserWithSameName = this.userService.create("New User", username, null, new String[0], false,
                group);
        assertThat(newUserWithSameName.getUsername()).isEqualTo(username);
    }
}
