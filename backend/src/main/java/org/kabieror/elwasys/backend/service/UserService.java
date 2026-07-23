package org.kabieror.elwasys.backend.service;

import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.events.UserChangedEvent;
import org.kabieror.elwasys.backend.exception.DuplicateCardIdException;
import org.kabieror.elwasys.backend.exception.DuplicateUsernameException;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stammdaten-Verwaltung für Benutzer (Phase 3 AP2, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/UserWindow} (Anlegen/Bearbeiten, OHNE den
 * dortigen Admin-Passwort-Reset-Teil - das ist AP4) sowie des Lösch-Verhaltens aus
 * {@code Portal/.../views/UsersView#deleteUser}.
 *
 * <p>Seit Phase 3 AP5 (siehe docs/kb/05-migration-plan.md, "Live-Updates zwischen Sessions")
 * veröffentlicht jede erfolgreiche Änderung ein {@link UserChangedEvent} über
 * {@link ApplicationEventPublisher} - die Ereignis-Auslösung liegt bewusst hier in der
 * Service-Schicht statt in der UI, damit sie unabhängig vom Aufrufer (Portal-UI oder künftig
 * die REST-API) immer feuert.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<UserEntity> findAllActive() {
        return this.userRepository.findByDeletedFalse();
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findById(Integer id) {
        return this.userRepository.findById(id);
    }

    /**
     * Legt einen neuen Benutzer an. 1:1-Portierung der Validierung aus
     * {@code UserWindow#save} (Mode {@code CREATE_USER}): jede angegebene Kartennummer darf
     * noch keinem anderen (nicht gelöschten) Benutzer zugeordnet sein.
     *
     * @throws DuplicateCardIdException wenn eine der Kartennummern bereits vergeben ist
     */
    @Transactional
    public UserEntity create(String name, String username, String email, String[] cardIds, boolean blocked,
            UserGroupEntity group) {
        assertUsernameIsFree(username, null);
        assertCardIdsAreFree(cardIds, null);
        UserEntity user = new UserEntity(name, username, group);
        user.setEmail(email);
        user.setCardIds(cardIds);
        user.setBlocked(blocked);
        user = this.userRepository.save(user);
        this.eventPublisher.publishEvent(new UserChangedEvent(user.getId()));
        return user;
    }

    /**
     * Bearbeitet einen bestehenden Benutzer. 1:1-Portierung von {@code UserWindow#save}
     * (Mode {@code EDIT_USER}) über {@code User#modify}: Admin-Flag und Benachrichtigungs-
     * Einstellungen sind hier bewusst nicht Teil der Signatur - für sie gibt es in diesem
     * Arbeitspaket keine UI (siehe docs/kb/05-migration-plan.md, AP2-Auftrag), sie bleiben daher
     * unverändert.
     *
     * @throws DuplicateCardIdException wenn eine der Kartennummern bereits einem ANDEREN
     *                                  Benutzer zugeordnet ist
     */
    @Transactional
    public UserEntity update(UserEntity user, String name, String username, String email, String[] cardIds,
            boolean blocked, UserGroupEntity group) {
        assertUsernameIsFree(username, user);
        assertCardIdsAreFree(cardIds, user);
        user.setName(name);
        user.setUsername(username);
        user.setEmail(email);
        user.setCardIds(cardIds);
        user.setBlocked(blocked);
        user.setGroup(group);
        user = this.userRepository.save(user);
        this.eventPublisher.publishEvent(new UserChangedEvent(user.getId()));
        return user;
    }

    /**
     * Löscht (weich) einen Benutzer. 1:1-Portierung von {@code User#setDeleted(true)}: der
     * Benutzername wird zusätzlich mit dem Präfix {@code #del<id>#} versehen - der Alt-Code
     * macht das bewusst, damit der ursprüngliche (eindeutige) Benutzername danach wieder für
     * eine Neuanmeldung frei ist ({@code users.username} trägt eine UNIQUE-Constraint, siehe
     * docs/kb/02-data-model.md). Buchungen/Historie des Benutzers bleiben erhalten (kein
     * physisches Löschen), analog zum Alt-Code.
     */
    @Transactional
    public void delete(UserEntity user) {
        user.setUsername("#del" + user.getId() + "#" + user.getUsername());
        user.setDeleted(true);
        user = this.userRepository.save(user);
        this.eventPublisher.publishEvent(new UserChangedEvent(user.getId()));
    }

    /**
     * Ändert die Selbstbedienungs-Einstellungen des angemeldeten Benutzers (Phase 3 AP4,
     * Testfall P17) - fachlicher Nachfolger von
     * {@code Portal/.../components/UserSettingsWindow#save}: dort werden ausschließlich
     * Email-Adresse, Email-Benachrichtigungs-Opt-in und Pushover-Key geändert (Name,
     * Username, Kartennummern, Gesperrt-Status, Admin-Flag, Benutzergruppe und das
     * elwaApp-Push-Flag bleiben unangetastet - letzteres liest dieselbe DB-Spalte wie
     * {@code emailNotification} NICHT, siehe {@code UserEntity#isPushNotification()}-Javadoc).
     */
    @Transactional
    public UserEntity updateOwnSettings(UserEntity user, String email, boolean emailNotification,
            String pushoverUserKey) {
        user.setEmail(email);
        user.setEmailNotification(emailNotification);
        user.setPushoverUserKey(pushoverUserKey == null ? "" : pushoverUserKey);
        user = this.userRepository.save(user);
        this.eventPublisher.publishEvent(new UserChangedEvent(user.getId()));
        return user;
    }

    /**
     * Stellt sicher, dass der Benutzername – case-insensitiv – noch keinem anderen, nicht
     * gelöschten Benutzer gehört (Issue #23, Pre-Launch AP4). Beim Bearbeiten wird der
     * bearbeitete Benutzer selbst ausgenommen (sein eigener, unveränderter Name kollidiert
     * nicht mit sich selbst). Vergleich case-insensitiv, weil der Login das ebenfalls tut und
     * genau diese Uneindeutigkeit ("Anna"/"anna") den späteren Login-Crash auslöst.
     */
    private void assertUsernameIsFree(String username, UserEntity userBeingEdited) {
        if (username == null || username.isBlank()) {
            return;
        }
        // 1:1 wie beim Speichern (UserEntity#setUsername) wird der Name klein geschrieben -
        // damit vergleicht der Guard denselben Wert, der später persistiert wird.
        String normalized = username.toLowerCase();
        if (userBeingEdited != null && normalized.equalsIgnoreCase(userBeingEdited.getUsername())) {
            // Der eigene, unveraenderte Name des bearbeiteten Benutzers ist kein Konflikt.
            return;
        }
        if (this.userRepository.existsByUsernameIgnoreCaseAndDeletedFalse(normalized)) {
            throw new DuplicateUsernameException(username);
        }
    }

    private void assertCardIdsAreFree(String[] cardIds, UserEntity userBeingEdited) {
        for (String cardId : cardIds) {
            if (cardId == null || cardId.isEmpty()) {
                continue;
            }
            Optional<UserEntity> owner = this.userRepository.findByCardId(cardId);
            if (owner.isPresent() && !owner.get().equals(userBeingEdited)) {
                throw new DuplicateCardIdException(cardId, owner.get());
            }
        }
    }
}
