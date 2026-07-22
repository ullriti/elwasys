package org.kabieror.elwasys.raspiclient.model;

import org.kabieror.elwasys.raspiclient.api.dto.UserDto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

/**
 * Client-seitiges Gegenstück zu {@code Common.User} (Phase 4 AP4). Siehe
 * {@link ClientProgram} für die Begründung, warum eigene, DB-freie Modellklassen statt
 * einer Wiederverwendung von {@code Common.User} gewählt wurden.
 * <p>
 * Enthält bewusst KEINE Benachrichtigungsfelder mehr ({@code emailNotification},
 * {@code pushoverUserKey}, {@code pushIonicId}/{@code isPushEnabled}) - der Versand läuft
 * seit Phase 4 AP3 zentral über das Backend anhand der dort gespeicherten
 * Benutzer-Einstellung, das Terminal löst nur noch das fachliche Ereignis aus (siehe
 * kb/05-migration-plan.md "Benachrichtigungen"). {@code authKey} (elwaApp-Kopplung) wird
 * bewusst NICHT übernommen: die Spalte war eine App-Altlast, die laut
 * {@code backend.domain.UserEntity} Klassenkommentar absichtlich NICHT ins neue Datenmodell
 * gemappt war und in Phase 5 AP4 (V10) entfernt wurde - siehe
 * {@code UserSettingsViewController}/{@code ConfirmationViewController} für die daraus
 * folgende (rein kosmetische) UI-Anpassung.
 */
public class ClientUser {

    private final int id;
    private String name;
    private String username;
    private String email;
    private boolean admin;
    private boolean blocked;
    private BigDecimal credit;

    /**
     * Die Ids der Geräte, die diesem Benutzer laut Backend zur Verfügung stehen (Phase 4
     * AP4) - entspricht {@code Common.Device#getValidUserGroups()
     * .contains(user.getGroup())} im Alt-Code, jetzt vom Server über
     * {@code DeviceDto#usableByUser()} berechnet ({@code PermissionService}, 1:1-Portierung
     * der Alt-Berechtigungslogik) statt lokal über clientseitig gehaltene
     * Benutzergruppen-Mitgliedschaften. Wird einmalig beim Kartenlogin befüllt (siehe
     * {@code MainFormController#onCardDetected}), damit
     * {@code DeviceListEntry#applyUserStyle} rein lokal (ohne weiteren Netzwerkaufruf pro
     * Gerätekachel) entscheiden kann, ob ein Gerät für diesen Benutzer verfügbar ist.
     */
    private Set<Integer> usableDeviceIds = Collections.emptySet();

    private ClientUser(int id) {
        this.id = id;
    }

    public static ClientUser of(UserDto dto) {
        ClientUser u = new ClientUser(dto.id());
        u.name = dto.name();
        u.username = dto.username();
        u.email = dto.email();
        u.admin = dto.admin();
        u.blocked = dto.blocked();
        u.credit = dto.credit();
        return u;
    }

    /**
     * Minimaler Benutzer, wie er für rein anzeigende Zwecke gebraucht wird, für die keine
     * eigene API-Anfrage lohnt (z. B. "letzter Benutzer" eines Geräts, aus
     * {@code DeviceOverviewDto#lastUserId()}/{@code #lastUserName()} - siehe
     * {@link ClientDevice}, oder der Benutzer einer beim Terminal-Start wiederaufgenommenen
     * Ausführung, Testfall C13). {@link #getCredit()} ist hier bewusst {@code null} - dieser
     * Pfad zeigt niemals Guthaben an.
     */
    public static ClientUser display(Integer id, String name) {
        ClientUser u = new ClientUser(id != null ? id : -1);
        u.name = name;
        return u;
    }

    /**
     * Entspricht {@code Common.User#getAnonymous()}: ein rein lokaler, nie an das Backend
     * übertragener virtueller Benutzer für die "Tür öffnen"-Funktion (siehe
     * {@link ClientExecution#offline}).
     */
    public static ClientUser anonymous() {
        ClientUser u = new ClientUser(-1);
        u.name = "-";
        u.credit = BigDecimal.ZERO;
        return u;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getUsername() {
        return this.username;
    }

    public String getEmail() {
        return this.email;
    }

    public boolean isAdmin() {
        return this.admin;
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    public BigDecimal getCredit() {
        return this.credit;
    }

    /**
     * Aktualisiert das lokal gehaltene Guthaben (z. B. nach {@code GET
     * /api/v1/users/{id}/credit}, siehe {@code MainFormController#updateUser}).
     */
    public void setCredit(BigDecimal credit) {
        this.credit = credit;
    }

    /**
     * 1:1-Portierung von {@code Common.User#canAfford}.
     */
    public boolean canAfford(BigDecimal price) {
        return this.credit != null && this.credit.compareTo(price) >= 0;
    }

    public void setUsableDeviceIds(Set<Integer> usableDeviceIds) {
        this.usableDeviceIds = usableDeviceIds;
    }

    /**
     * Ob das Gerät mit der gegebenen Id diesem Benutzer laut Backend zur Verfügung steht
     * (siehe {@link #usableDeviceIds}).
     */
    public boolean isDeviceUsable(int deviceId) {
        return this.usableDeviceIds.contains(deviceId);
    }
}
