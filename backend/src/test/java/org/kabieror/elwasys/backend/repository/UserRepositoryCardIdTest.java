package org.kabieror.elwasys.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 1:1-Portierung der Kartennummer-Suche aus {@code DataManager#getUserByCardId}: mehrere
 * Kartennummern werden zeilenweise (per {@code \n}) in einer Spalte gespeichert, das
 * Matching erfolgt per Postgres-Regex {@code (?n)^cardId$} - exakter Treffer EINER Zeile,
 * kein Teilstring-Treffer (siehe kb/05-migration-plan.md, AP2, {@link UserRepository}).
 */
class UserRepositoryCardIdTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void findsUserByExactCardIdAmongMultipleLines() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Card User"), Fixtures.unique("card-user"), group);
        String cardA = Fixtures.unique("CARD-A");
        String cardB = Fixtures.unique("CARD-B");
        user.setCardIds(new String[] {cardA, cardB});
        user = this.userRepository.save(user);

        assertThat(this.userRepository.findByCardId(cardA)).map(UserEntity::getId).contains(user.getId());
        assertThat(this.userRepository.findByCardId(cardB)).map(UserEntity::getId).contains(user.getId());
    }

    @Test
    void doesNotMatchAPartialCardId() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Card User"), Fixtures.unique("card-user"), group);
        String card = Fixtures.unique("FULLCARD123456");
        user.setCardIds(new String[] {card});
        this.userRepository.save(user);

        Optional<UserEntity> byPrefix = this.userRepository.findByCardId(card.substring(0, card.length() - 2));
        assertThat(byPrefix).as("a partial match must not be found - the old regex is anchored with ^ and $")
                .isEmpty();
    }

    @Test
    void deletedUsersAreNotFoundByCardId() {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = new UserEntity(Fixtures.unique("Deleted Card User"), Fixtures.unique("deleted-card-user"),
                group);
        String card = Fixtures.unique("DELCARD");
        user.setCardIds(new String[] {card});
        user.setDeleted(true);
        this.userRepository.save(user);

        assertThat(this.userRepository.findByCardId(card)).isEmpty();
    }
}
