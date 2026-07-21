package org.kabieror.elwasys.backend.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standort-Snapshot für die Offline-Buchungs-Vorbereitung (AP3, Phase 4, siehe
 * kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am Terminal", Punkt 1 "Lokaler
 * Daten-Snapshot"): "Nutzer mit Kartennummern, Guthaben, Sperr-Status, Geräte/Programme/
 * Preise, Berechtigungen" mit Zeitstempel. <b>Enthält bewusst KEINE Passwort-Hashes</b>
 * (siehe {@link SnapshotUserDto}).
 *
 * <p><b>Scope-Entscheidung (siehe kb/03-modules.md für die Begründung)</b>: {@code users}
 * ist auf Benutzer beschränkt, deren Gruppe an DIESEM Standort zugelassen ist (({@code
 * userGroups} enthält entsprechend nur die Standort-Gruppen selbst), statt ALLE Benutzer des
 * Systems auszuliefern - ein Terminal muss offline nur wissen, wer bei ihm einchecken darf.
 * Eine Karte eines an diesem Standort nicht zugelassenen Nutzers wird offline dadurch wie
 * eine unbekannte Karte behandelt (nicht wie ein spezifisches "an diesem Standort nicht
 * erlaubt") - eine bewusste, für die Offline-Entscheidung ausreichend konservative
 * Vereinfachung (siehe Konzeptskizze Punkt 2 "Regeln bewusst konservativ").
 *
 * <p>{@code devices}/{@code programs} sind auf den Standort bzw. die dort verfügbaren
 * Programme beschränkt (analog {@code GET /api/v1/devices}, aber ohne {@code userId} -
 * siehe {@code DeviceOverviewDto}).
 *
 * <p>Dieses Arbeitspaket (AP3) liefert nur die DATEN aus - die eigentliche
 * Offline-Entscheidungslogik (Kartenlogin/Berechtigungs-/Guthabenprüfung gegen den Snapshot,
 * Ereignis-Journal, Replay) ist laut Roadmap AP6 vorbehalten.
 */
public record SnapshotDto(Integer locationId, String locationName, LocalDateTime generatedAt,
        List<SnapshotUserGroupDto> userGroups, List<SnapshotUserDto> users, List<SnapshotDeviceDto> devices,
        List<SnapshotProgramDto> programs) {
}
