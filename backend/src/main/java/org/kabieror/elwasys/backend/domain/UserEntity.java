package org.kabieror.elwasys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Entspricht der Tabelle {@code users} (siehe kb/02-data-model.md) sowie
 * {@code org.kabieror.elwasys.common.User} im Alt-Code.
 *
 * <p>Die App-Relikt-Spalten {@code app_id}, {@code access_key}, {@code auth_key} waren
 * bewusst NICHT gemappt (siehe kb/05-migration-plan.md, Rahmenbedingungen) - alle drei
 * waren nullable und für die Fachlogik (Abrechnung/Berechtigungen/Preisberechnung/
 * Execution-Lebenszyklus) irrelevant. Sie wurden inkl. des DB-Triggers
 * {@code user_authkey_trigger} (befüllte {@code auth_key} bei jedem INSERT) in Phase 5
 * AP4 (V10) entfernt.
 */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(length = 50)
    private String email;

    /**
     * Rohwert der Spalte {@code card_ids}: mehrere RFID-Kartennummern, durch Zeilenumbruch
     * getrennt (siehe {@link #getCardIds()}/{@link #setCardIds(String[])}, 1:1 wie
     * {@code org.kabieror.elwasys.common.User}).
     */
    @Column(name = "card_ids", nullable = false)
    private String cardIds = "";

    @Column(nullable = false)
    private boolean blocked = false;

    @Column(length = 50)
    private String password;

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @Column(name = "email_notification", nullable = false)
    private boolean emailNotification = true;

    @Column(name = "push_notification", nullable = false)
    private boolean pushNotification = true;

    @Column(name = "pushover_user_key", nullable = false, length = 50)
    private String pushoverUserKey = "";

    @Column(name = "password_reset_key", length = 50)
    private String passwordResetKey;

    @Column(name = "password_reset_timeout")
    private LocalDateTime passwordResetTimeout;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * EAGER (nicht LAZY): der Alt-Code (siehe {@code User#load}) lädt die Gruppe eines
     * Benutzers immer sofort mit ({@code dataManager.getUserGroupById(...)}), es gibt dort
     * kein "lazy" Nachladen. Diese Entity bildet das nach - siehe kb/05-migration-plan.md
     * ("Entscheidungen": alle in AP2 fachlich genutzten Assoziationen sind EAGER, analog
     * zum durchgängig eager ladenden Alt-{@code DataManager}; AP2 hat noch keine
     * Web-/Transaktionsgrenze, die LAZY sauber absichern würde - das kann in einem
     * späteren Arbeitspaket mit gezielten Fetch-Joins/DTOs verfeinert werden).
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private UserGroupEntity group;

    protected UserEntity() {
        // for JPA
    }

    public UserEntity(String name, String username, UserGroupEntity group) {
        this.name = name;
        this.username = username == null ? null : username.toLowerCase();
        this.group = group;
    }

    public Integer getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.toLowerCase();
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Gibt die Kartennummern des Benutzers zurück - 1:1 wie
     * {@code org.kabieror.elwasys.common.User#getCardIds()} (Aufsplitten des Rohwerts an
     * {@code \n}).
     */
    public String[] getCardIds() {
        return this.cardIds.split("\n");
    }

    /**
     * Setzt die Kartennummern - 1:1 wie {@code org.kabieror.elwasys.common.User} beim
     * Anlegen/Ändern (Zusammenfügen mit {@code \n} als Trenner).
     */
    public void setCardIds(String[] cardIds) {
        this.cardIds = String.join("\n", cardIds);
    }

    public String getCardIdsRaw() {
        return this.cardIds;
    }

    public void setCardIdsRaw(String cardIds) {
        this.cardIds = cardIds;
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAdmin() {
        return this.admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isEmailNotification() {
        return this.emailNotification;
    }

    public void setEmailNotification(boolean emailNotification) {
        this.emailNotification = emailNotification;
    }

    public boolean isPushNotification() {
        return this.pushNotification;
    }

    public void setPushNotification(boolean pushNotification) {
        this.pushNotification = pushNotification;
    }

    public String getPushoverUserKey() {
        return this.pushoverUserKey;
    }

    public void setPushoverUserKey(String pushoverUserKey) {
        this.pushoverUserKey = pushoverUserKey;
    }

    public String getPasswordResetKey() {
        return this.passwordResetKey;
    }

    public void setPasswordResetKey(String passwordResetKey) {
        this.passwordResetKey = passwordResetKey;
    }

    public LocalDateTime getPasswordResetTimeout() {
        return this.passwordResetTimeout;
    }

    public void setPasswordResetTimeout(LocalDateTime passwordResetTimeout) {
        this.passwordResetTimeout = passwordResetTimeout;
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getLastLogin() {
        return this.lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public UserGroupEntity getGroup() {
        return this.group;
    }

    public void setGroup(UserGroupEntity group) {
        this.group = group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "UserEntity{id=" + this.id + ", username=" + this.username + ", cardIds="
                + Arrays.toString(this.getCardIds()) + "}";
    }
}
