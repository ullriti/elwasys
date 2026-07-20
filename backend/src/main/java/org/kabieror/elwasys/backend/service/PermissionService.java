package org.kabieror.elwasys.backend.service;

import java.util.List;
import java.util.stream.Collectors;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.springframework.stereotype.Service;

/**
 * 1:1-Portierung der Berechtigungsprüfungen, die im Alt-Code über den Client verstreut
 * sind (nicht in {@code DataManager}, sondern direkt in den UI-Controllern - siehe
 * kb/05-migration-plan.md, AP2): {@code MainFormController} (Standort-Login),
 * {@code DeviceListEntry} (Gerätezugriff), {@code Device#getPrograms(User)}
 * (Programmauswahl).
 */
@Service
public class PermissionService {

    /**
     * 1:1-Portierung der Login-Prüfung aus
     * {@code ui/medium/MainFormController#onCardDetected}: ein Benutzer darf einen
     * Standort nur verwenden, wenn er nicht gesperrt ist UND seine Gruppe am Standort
     * zugelassen ist.
     */
    public boolean isUserAllowedAtLocation(UserEntity user, LocationEntity location) {
        return !user.isBlocked() && location.getValidUserGroups().contains(user.getGroup());
    }

    /**
     * 1:1-Portierung der Prüfung aus
     * {@code ui/medium/controller/DeviceListEntry#applyUserStyle}: ein Gerät ist für einen
     * Benutzer nur nutzbar, wenn es aktiviert ist UND seine Gruppe am Gerät zugelassen ist.
     */
    public boolean isDeviceUsableByUser(DeviceEntity device, UserEntity user) {
        return device.isEnabled() && device.getValidUserGroups().contains(user.getGroup());
    }

    /**
     * 1:1-Portierung von {@code org.kabieror.elwasys.common.Device#getPrograms(User)}: alle
     * dem Gerät zugeordneten Programme, deren zulässige Benutzergruppen die Gruppe des
     * Benutzers enthalten.
     *
     * <p>Beobachtung (siehe kb/05-migration-plan.md): der Alt-Code filtert hier NICHT
     * zusätzlich auf {@code program.isEnabled()} - ein deaktiviertes Programm bleibt für
     * den Client sichtbar/wählbar, solange es dem Gerät zugeordnet und die Benutzergruppe
     * berechtigt ist. Das wird hier bewusst identisch nachgebildet (kein zusätzlicher
     * enabled-Filter).
     */
    public List<ProgramEntity> getAvailablePrograms(DeviceEntity device, UserEntity user) {
        return device.getPrograms().stream().filter(p -> p.getValidUserGroups().contains(user.getGroup()))
                .collect(Collectors.toList());
    }

    /**
     * Prüft, ob ein einzelnes Programm für ein Gerät und einen Benutzer verfügbar ist
     * (Kurzform von {@link #getAvailablePrograms(DeviceEntity, UserEntity)} für ein
     * bestimmtes Programm, z.B. zur serverseitigen Validierung eines Programmstarts).
     */
    public boolean isProgramAvailableForDeviceAndUser(DeviceEntity device, ProgramEntity program, UserEntity user) {
        return device.getPrograms().contains(program) && program.getValidUserGroups().contains(user.getGroup());
    }
}
