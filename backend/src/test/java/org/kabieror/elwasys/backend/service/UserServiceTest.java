package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.DuplicateCardIdException;
import org.kabieror.elwasys.backend.exception.DuplicateUsernameException;
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

    /**
     * Issue #23 (Pre-Launch AP4): der case-insensitive Login würde bei zwei nur in der
     * Schreibweise abweichenden Benutzernamen dauerhaft crashen. Der Service-Guard verhindert,
     * dass eine solche Kollision überhaupt entsteht - hier existiert bereits "anna" (klein
     * gespeichert), das Anlegen von "Anna" muss sprechend scheitern.
     */
    @Test
    void creatingAUserWhoseUsernameOnlyDiffersInCaseFails() {
        UserGroupEntity group = group();
        String base = Fixtures.unique("anna");
        this.userService.create("Anna Existing", base.toLowerCase(), null, new String[0], false, group);

        assertThatThrownBy(() -> this.userService.create("Anna Duplicate", base.toUpperCase(), null, new String[0],
                false, group)).isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void updatingAUserKeepingItsOwnUsernameDoesNotFail() {
        UserGroupEntity group = group();
        String username = Fixtures.unique("keep");
        UserEntity user = this.userService.create("Keeper", username, null, new String[0], false, group);

        // Der eigene, unveränderte (nur anders geschriebene) Name darf nicht mit sich selbst
        // kollidieren.
        UserEntity updated = this.userService.update(user, "Keeper Renamed", username.toUpperCase(), null,
                new String[0], false, group);

        assertThat(updated.getName()).isEqualTo("Keeper Renamed");
    }

    /**
     * Issue #21 (Pre-Launch AP4): {@code assertCardIdsAreFree} nutzt {@code findByCardId}, das
     * früher regex-basiert war. Eine Kartennummer mit Regex-Metazeichen ({@code .*}) hätte
     * damit fälschlich jede bestehende Karte als Kollision gemeldet. Mit der regex-freien
     * Suche wird ".*" literal behandelt und kollidiert nicht mit einer echten Karte.
     */
    @Test
    void creatingAUserWithARegexMetacharacterCardIdDoesNotFalselyCollide() {
        UserGroupEntity group = group();
        this.userService.create("Card Owner", Fixtures.unique("owner"), null,
                new String[] {Fixtures.unique("REALCARD")}, false, group);

        // ".*" ist keine echte Karte -> darf NICHT als Duplikat der obigen Karte gelten.
        UserEntity created = this.userService.create("Regex Owner", Fixtures.unique("regex"), null,
                new String[] {".*"}, false, group);

        assertThat(created.getId()).isNotNull();
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

    /**
     * Issue #39 (Pre-Launch AP5): Ein Benutzername nahe der Spaltenbreite ließ das Lösch-Präfix
     * {@code #del<id>#} die Spalte {@code users.username VARCHAR(50)} sprengen - der Alt-Code
     * lief in einen DB-Fehler. Der Name wird jetzt auf 50 Zeichen gekürzt (Präfix priorisiert),
     * das Löschen schlägt nicht mehr fehl und ist idempotent.
     */
    @Test
    void deletingAUserWithAVeryLongUsernameTruncatesToColumnWidthAndIsIdempotent() {
        UserGroupEntity group = group();
        // 45 Zeichen (< 50, gültig anzulegen), aber Präfix + Name überschreitet 50.
        String longUsername = Fixtures.unique("x".repeat(36)).toLowerCase();
        assertThat(longUsername.length()).isEqualTo(45);
        UserEntity user = this.userService.create("Long Name", longUsername, null, new String[0], false, group);
        Integer id = user.getId();

        this.userService.delete(user);

        UserEntity reloaded = this.userRepository.findById(id).orElseThrow();
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getUsername()).as("auf Spaltenbreite gekürzt").hasSize(50);
        assertThat(reloaded.getUsername()).startsWith("#del" + id + "#");
        String truncatedAfterFirstDelete = reloaded.getUsername();

        // Idempotent: ein zweites delete() darf den bereits gelöschten Namen nicht erneut
        // präfixieren (kein "#del<id>##del<id>#...").
        this.userService.delete(reloaded);
        assertThat(this.userRepository.findById(id).orElseThrow().getUsername())
                .isEqualTo(truncatedAfterFirstDelete);

        // Der Original-Name ist frei geworden.
        UserEntity reused = this.userService.create("Reuse", longUsername, null, new String[0], false, group);
        assertThat(reused.getUsername()).isEqualTo(longUsername);
    }
}
