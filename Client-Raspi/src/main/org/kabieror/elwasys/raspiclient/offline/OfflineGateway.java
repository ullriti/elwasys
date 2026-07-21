package org.kabieror.elwasys.raspiclient.offline;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kabieror.elwasys.raspiclient.api.ApiClient;
import org.kabieror.elwasys.raspiclient.api.ApiException;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceDto;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceOverviewDto;
import org.kabieror.elwasys.raspiclient.api.dto.ExecutionDto;
import org.kabieror.elwasys.raspiclient.api.dto.ProgramDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotDeviceDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotProgramDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotUserDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotUserGroupDto;
import org.kabieror.elwasys.raspiclient.api.dto.UserDto;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.model.ClientUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offline-Entscheidungslogik und Journal-Replay (Phase 4 AP6, siehe kb/05-migration-plan.md
 * "Konzeptskizze: Offline-Buchungen am Terminal" Punkte 2, 4 und 6, sowie den AP6-Auftrag).
 * Zentrale Anlaufstelle, die {@code application.ElwaManager} für Kartenlogin, Geräte-/
 * Programmlisten und das Anlegen neuer Ausführungen benutzt, WENN der direkte
 * {@link ApiClient}-Aufruf mit einem reinen Kommunikationsfehler
 * ({@link ApiException#isCommunicationFailure()}) fehlgeschlagen ist.
 * <p>
 * <b>Entwurfsprinzip</b>: jede Methode liefert GENAU dieselben Wire-DTOs
 * ({@link UserDto}/{@link DeviceDto}/{@link DeviceOverviewDto}) bzw. dieselbe
 * fachliche Fehler-Vokabular ({@link ApiException#is(int, String)}-kompatible Statuscodes/
 * Slugs) wie der Online-Pfad - dadurch bleibt der komplette UI-/Modell-Code
 * ({@code ClientUser#of}, {@code ClientDevice#updateFrom}, {@code ClientProgram#of}, die
 * Fehlerbehandlung in {@code MainFormController#onCardDetected}) UNVERÄNDERT nutzbar, nur
 * die Datenquelle wechselt (Auftrag: "der Bedienfluss ... bleibt identisch, nur die
 * Datenquelle ist der Snapshot").
 * <p>
 * <b>Konservative Grundregel</b> (Konzeptskizze Punkt 2 "Regeln bewusst konservativ"): kann
 * eine Anfrage mangels (aktuellem) Snapshot gar nicht offline beantwortet werden, wird die
 * URSPRÜNGLICHE {@link ApiException} (der Kommunikationsfehler) unverändert weitergereicht -
 * der Aufrufer sieht dann exakt das bestehende C15-Fehlerbild, es wird keine neue,
 * irreführende Fehlerkategorie erfunden.
 */
public class OfflineGateway {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ApiClient apiClient;
    private final OfflineSnapshotStore snapshotStore;
    private final OfflineJournal journal;

    public OfflineGateway(ApiClient apiClient, OfflineSnapshotStore snapshotStore, OfflineJournal journal) {
        this.apiClient = apiClient;
        this.snapshotStore = snapshotStore;
        this.journal = journal;
    }

    // --- Offline-Entscheidungslogik (Konzeptskizze Punkt 2) ---------------------------------

    /**
     * Kartenlogin gegen den Snapshot (Gegenstück zu {@code ApiClient#cardLogin}). Wirft
     * dieselben Fehler-Slugs wie der Online-Pfad ({@code card-not-found}/{@code user-blocked})
     * - {@code location-not-allowed} kann hier NIE auftreten, weil der Snapshot bereits nur
     * die an diesem Standort zugelassenen Benutzer enthält (siehe {@link SnapshotDto}
     * Javadoc "Scope-Entscheidung").
     */
    public UserDto cardLogin(String cardId, ApiException originalFailure) throws ApiException {
        SnapshotDto snapshot = usableSnapshotOrRethrow(originalFailure);

        SnapshotUserDto match = null;
        for (SnapshotUserDto u : snapshot.users()) {
            if (u.cardIds().contains(cardId)) {
                match = u;
                break;
            }
        }
        if (match == null) {
            throw new ApiException(404, "card-not-found", "Karte unbekannt",
                    "Zu dieser Karte ist offline kein Benutzer bekannt.");
        }
        if (match.blocked()) {
            throw new ApiException(403, "user-blocked", "Benutzer gesperrt", "Der Benutzer ist gesperrt.");
        }

        BigDecimal offlineDebit = this.journal.computeOfflineDebits().getOrDefault(match.id(), BigDecimal.ZERO);
        BigDecimal cachedCredit = match.credit() != null ? match.credit() : BigDecimal.ZERO;
        this.logger.info("Offline-Kartenlogin erfolgreich fuer Benutzer '{}' (Snapshot vom {}).", match.name(),
                snapshot.generatedAt());
        return new UserDto(match.id(), match.name(), match.name(), null, false, false, match.groupId(), null,
                cachedCredit.subtract(offlineDebit));
    }

    /**
     * Gegenstück zu {@code ApiClient#getDevices(int)}: die für einen Benutzer nutzbaren
     * Geräte inkl. gruppengefilterter, gerabattet bepreister Programme.
     */
    public List<DeviceDto> getDevicesForUser(int userId, ApiException originalFailure) throws ApiException {
        SnapshotDto snapshot = usableSnapshotOrRethrow(originalFailure);
        SnapshotUserDto user = findUser(snapshot, userId);
        if (user == null) {
            throw originalFailure;
        }
        SnapshotUserGroupDto group = findGroup(snapshot, user.groupId());

        List<DeviceDto> result = new ArrayList<>();
        for (SnapshotDeviceDto d : snapshot.devices()) {
            boolean usable = user.groupId() != null && d.validUserGroupIds().contains(user.groupId());
            List<ProgramDto> programs = new ArrayList<>();
            for (Integer programId : d.programIds()) {
                SnapshotProgramDto p = findProgram(snapshot, programId);
                if (p == null || user.groupId() == null || !p.validUserGroupIds().contains(user.groupId())) {
                    continue;
                }
                programs.add(toProgramDto(p, group));
            }
            result.add(new DeviceDto(d.id(), d.name(), d.position(), d.enabled(), usable, false, programs,
                    d.fhemName(), d.fhemSwitchName(), d.fhemPowerName(), d.deconzUuid(), d.autoEndPowerThreashold(),
                    d.autoEndWaitTimeSeconds()));
        }
        return result;
    }

    /**
     * Gegenstück zu {@code ApiClient#getDevicesOverview()}: die anonyme, ungerabattete
     * Geräteübersicht des Standorts. {@code occupied}/{@code runningExecutionId}/
     * {@code lastUserId}/{@code lastUserName} bleiben offline leer - Belegung wird für
     * bereits laufende Ausführungen ohnehin rein lokal über
     * {@code ClientDevice#getCurrentExecution()} nachgehalten (Konzeptskizze Punkt 6:
     * "ein Gerät = ein Terminal", der Belegungsstatus ist daher OHNE Netzwerkaufruf schon
     * korrekt bekannt); ein Wiederaufnahme-Scan (Testfall C13) über einen bereits VOR dem
     * ersten Terminal-Start laufenden, dem Terminal noch unbekannten Datensatz ist offline
     * naturgemäß nicht möglich (dokumentiertes Restrisiko, siehe Abschlussbericht).
     */
    public List<DeviceOverviewDto> getDevicesOverview(ApiException originalFailure) throws ApiException {
        SnapshotDto snapshot = usableSnapshotOrRethrow(originalFailure);

        List<DeviceOverviewDto> result = new ArrayList<>();
        for (SnapshotDeviceDto d : snapshot.devices()) {
            List<ProgramDto> programs = new ArrayList<>();
            for (Integer programId : d.programIds()) {
                SnapshotProgramDto p = findProgram(snapshot, programId);
                if (p != null) {
                    programs.add(toProgramDto(p, null));
                }
            }
            result.add(new DeviceOverviewDto(d.id(), d.name(), d.position(), d.enabled(), false, null, null, null,
                    d.fhemName(), d.fhemSwitchName(), d.fhemPowerName(), d.deconzUuid(), d.autoEndPowerThreashold(),
                    d.autoEndWaitTimeSeconds(), programs));
        }
        return result;
    }

    /**
     * Bucht eine neue Ausführung offline (Konzeptskizze Punkt 2 "lokal aufgelaufene
     * Offline-Buchungen werden vom gecachten Guthaben abgezogen" + Punkt 3 "Persistentes
     * Ereignis-Journal"): prüft Berechtigung/Belegung/Guthaben gegen den Snapshot bzw. den
     * lokal bekannten Gerätezustand, hinterlegt bei Erfolg einen {@code START}-Eintrag im
     * Journal und liefert eine {@link ClientExecution#isOfflinePendingReplay() offline
     * gebuchte} Ausführung zurück - wirft andernfalls denselben Fehler wie der entsprechende
     * Online-Fall ({@code POST /api/v1/executions}).
     *
     * @param clientTimestamp der Buchungszeitpunkt (wird 1:1 als Original-Zeitstempel für
     *                        den späteren Replay verwendet)
     * @param idempotencyKey  der für diese Buchung zu verwendende Idempotenz-Schlüssel (vom
     *                        Aufrufer erzeugt, siehe {@code application.ElwaManager
     *                        #createExecution})
     */
    public ClientExecution createExecution(ClientUser user, ClientDevice device, ClientProgram program,
            LocalDateTime clientTimestamp, String idempotencyKey, ApiException originalFailure) throws ApiException {
        SnapshotDto snapshot = usableSnapshotOrRethrow(originalFailure);

        SnapshotUserDto snapUser = findUser(snapshot, user.getId());
        SnapshotDeviceDto snapDevice = findDevice(snapshot, device.getId());
        SnapshotProgramDto snapProgram = findProgram(snapshot, program.getId());
        if (snapUser == null || snapDevice == null || snapProgram == null) {
            // Unbekannt im (evtl. veralteten) Snapshot - z.B. ein erst nach der letzten
            // Aktualisierung angelegtes Gerät/Programm. Konservativ: wie "Backend nicht
            // erreichbar" behandeln statt zu raten.
            throw originalFailure;
        }
        if (snapUser.blocked()) {
            throw new ApiException(403, "user-blocked", "Benutzer gesperrt", "Der Benutzer ist gesperrt.");
        }
        if (snapUser.groupId() == null || !snapDevice.validUserGroupIds().contains(snapUser.groupId())) {
            throw new ApiException(403, "device-not-usable", "Gerät nicht nutzbar",
                    "Der Benutzer darf dieses Gerät nicht offline nutzen.");
        }
        if (!snapDevice.programIds().contains(snapProgram.id())
                || !snapProgram.validUserGroupIds().contains(snapUser.groupId())) {
            throw new ApiException(403, "program-not-available", "Programm nicht verfügbar",
                    "Das Programm ist für diesen Benutzer offline nicht verfügbar.");
        }
        if (device.getCurrentExecution() != null) {
            // Belegung ist rein lokal bekannt (Single-Writer-Annahme, siehe Klassenkommentar
            // getDevicesOverview) - kein Snapshot-Zugriff noetig.
            throw new ApiException(409, "device-occupied", "Gerät belegt", "Das Gerät ist bereits belegt.");
        }
        BigDecimal maxPrice = program.getPriceAtMaxDuration();
        BigDecimal offlineDebit = this.journal.computeOfflineDebits().getOrDefault(user.getId(), BigDecimal.ZERO);
        BigDecimal cachedCredit = snapUser.credit() != null ? snapUser.credit() : BigDecimal.ZERO;
        BigDecimal available = cachedCredit.subtract(offlineDebit);
        if (maxPrice != null && available.compareTo(maxPrice) < 0) {
            throw new ApiException(402, "insufficient-credit", "Guthaben unzureichend",
                    "Das gecachte Guthaben reicht für diese Buchung nicht aus.");
        }

        this.journal.appendStart(idempotencyKey, clientTimestamp, user.getId(), device.getId(), program.getId());
        this.logger.info(
                "Offline-Buchung angelegt: Benutzer '{}', Geraet '{}', Programm '{}' (Idempotenz-Schluessel '{}', "
                        + "Snapshot vom {}).", user.getName(), device.getName(), program.getName(), idempotencyKey,
                snapshot.generatedAt());
        return ClientExecution.pendingOfflineReplay((int) (System.nanoTime() & 0x7fffffff), device, program, user,
                clientTimestamp, idempotencyKey);
    }

    // --- Ereignis-Journal (Stufe A + Stufe B, Konzeptskizze Punkt 3) ------------------------

    /**
     * Hinterlegt das Ende/den Abbruch einer Ausführung im Journal (aufgerufen von
     * {@code executions.ExecutionFinisher}, sowohl für Stufe A [eine bereits online
     * gestartete Ausführung, deren Beenden-Meldung an einem Kommunikationsfehler
     * scheiterte] als auch für Stufe B [eine offline angelegte, noch nicht nachgemeldete
     * Ausführung - dort wird IMMER journaliert, nie ein Live-Aufruf versucht, siehe
     * {@link ClientExecution} Klassenkommentar]).
     */
    public void appendFinishOrAbort(ClientExecution execution, boolean aborted, LocalDateTime clientTimestamp,
            String idempotencyKey) {
        BigDecimal chargedPrice = computeChargedPrice(execution);
        Integer executionId = execution.isOfflinePendingReplay() ? null : execution.getId();
        String startKey = execution.isOfflinePendingReplay() ? execution.getOfflinePendingIdempotencyKey() : null;
        this.journal.appendFinish(aborted, idempotencyKey, clientTimestamp, execution.getUser().getId(), executionId,
                startKey, chargedPrice);
        this.logger.info(
                "{} lokal vermerkt und im Offline-Journal hinterlegt: Ausfuehrung auf Geraet '{}' (Idempotenz-"
                        + "Schluessel '{}').", aborted ? "Abbruch" : "Ende", execution.getDevice().getName(),
                idempotencyKey);
    }

    /**
     * Verwirft eine offline angelegte, noch nicht nachgemeldete Buchung wieder (z. B. weil
     * das Einschalten der Steckdose danach fehlschlug - {@code executions.ExecutionManager
     * #resetOnFailure} - analog zu {@code ApiClient#resetExecution} im Online-Fall, hier
     * aber rein lokal, weil beim Backend noch gar keine Ausführung existiert).
     */
    public void cancelPendingStart(String startIdempotencyKey) {
        this.journal.removeEntry(startIdempotencyKey);
        this.logger.info(
                "Offline-Buchung mit Idempotenz-Schluessel '{}' verworfen (Steckdose liess sich nicht einschalten).",
                startIdempotencyKey);
    }

    private BigDecimal computeChargedPrice(ClientExecution execution) {
        SnapshotDto snapshot = this.snapshotStore.get();
        if (snapshot == null) {
            return BigDecimal.ZERO;
        }
        SnapshotProgramDto p = findProgram(snapshot, execution.getProgram().getId());
        if (p == null) {
            return BigDecimal.ZERO;
        }
        SnapshotUserDto u = findUser(snapshot, execution.getUser().getId());
        SnapshotUserGroupDto group = u == null || u.groupId() == null ? null : findGroup(snapshot, u.groupId());
        Duration elapsed = execution.getElapsedTime();
        Duration maxDuration = Duration.ofSeconds(p.maxDurationSeconds());
        if (elapsed.compareTo(maxDuration) > 0) {
            elapsed = maxDuration;
        }
        BigDecimal price = OfflinePricing.price(p, elapsed, group);
        return price == null ? BigDecimal.ZERO : price;
    }

    // --- Replay (Konzeptskizze Punkt 4) ------------------------------------------------------

    /**
     * Überträgt das komplette Journal in Reihenfolge über die Execution-Endpunkte (siehe
     * kb/03-modules.md "Idempotenz + Replay"). Bricht bei JEDEM Fehler (Kommunikations- wie
     * fachlicher Fehler) sofort ab und lässt das Journal UNVERÄNDERT - der nächste Versuch
     * beginnt wieder ganz von vorn. Das ist sicher (nicht nur "wieder-anlaufend"), weil das
     * Backend über den {@code Idempotency-Key}-Header dedupliziert (siehe
     * {@code IdempotencyService}): ein erneuter Versuch eines bereits erfolgreich verarbeiteten
     * Eintrags liefert nur die gespeicherte Antwort erneut, ohne die fachliche Aktion ein
     * zweites Mal auszulösen.
     *
     * @return {@code true}, wenn das Journal vollständig geleert werden konnte
     */
    public boolean replay() {
        List<OfflineJournalEntry> entries = this.journal.readAll();
        if (entries.isEmpty()) {
            return true;
        }
        this.logger.info("Starte Offline-Journal-Replay ueber {} Eintrag/Eintraege.", entries.size());
        Map<String, Integer> resolvedStartKeys = new HashMap<>();
        for (OfflineJournalEntry entry : entries) {
            try {
                replayOne(entry, resolvedStartKeys);
            } catch (ApiException e) {
                this.logger.warn(
                        "Offline-Journal-Replay bei Eintrag '{}' ({}) unterbrochen - wird beim naechsten Versuch "
                                + "komplett erneut versucht (das Backend dedupliziert bereits verarbeitete "
                                + "Eintraege ueber den Idempotenz-Schluessel, ein erneuter Versuch ist sicher).",
                        entry.idempotencyKey(), entry.type(), e);
                return false;
            }
        }
        this.journal.clear();
        this.logger.info("Offline-Journal-Replay abgeschlossen: {} Eintrag/Eintraege erfolgreich nachgemeldet.",
                entries.size());
        return true;
    }

    private void replayOne(OfflineJournalEntry entry, Map<String, Integer> resolvedStartKeys) throws ApiException {
        switch (entry.type()) {
            case OfflineJournalEntry.TYPE_START -> {
                ExecutionDto dto = this.apiClient.createExecution(entry.userId(), entry.deviceId(),
                        entry.programId(), entry.clientTimestamp(), entry.idempotencyKey());
                resolvedStartKeys.put(entry.idempotencyKey(), dto.id());
            }
            case OfflineJournalEntry.TYPE_FINISH, OfflineJournalEntry.TYPE_ABORT -> {
                int executionId = entry.executionId() != null ? entry.executionId()
                        : resolvedStartKeys.get(entry.startIdempotencyKey());
                if (OfflineJournalEntry.TYPE_FINISH.equals(entry.type())) {
                    this.apiClient.finishExecution(executionId, entry.clientTimestamp(), entry.idempotencyKey());
                } else {
                    this.apiClient.abortExecution(executionId, entry.clientTimestamp(), entry.idempotencyKey());
                }
            }
            default -> this.logger.warn("Unbekannter Journal-Eintragstyp '{}' - wird uebersprungen.", entry.type());
        }
    }

    // --- Snapshot-Aktualisierung -------------------------------------------------------------

    /**
     * Lädt einen frischen Snapshot vom Backend und persistiert ihn - best effort, still
     * ignoriert bei einem Kommunikationsfehler (dann bleibt der zuletzt bekannte Snapshot
     * gültig, bis er selbst durch Zeitablauf zu alt wird).
     */
    public void refreshSnapshot() {
        try {
            SnapshotDto snapshot = this.apiClient.getSnapshot();
            this.snapshotStore.save(snapshot);
        } catch (ApiException e) {
            this.logger.debug("Konnte den Offline-Snapshot nicht aktualisieren (Backend nicht erreichbar?).", e);
        }
    }

    // --- Hilfsmethoden -------------------------------------------------------------------------

    private SnapshotDto usableSnapshotOrRethrow(ApiException originalFailure) throws ApiException {
        SnapshotDto snapshot = this.snapshotStore.get();
        if (snapshot == null || isExpired(snapshot)) {
            throw originalFailure;
        }
        return snapshot;
    }

    /**
     * Ob aktuell ein nicht abgelaufener Snapshot verfügbar ist - genutzt von
     * {@code application.ElwaManager#initiate} für die Offline-Start-Entscheidung (Backend
     * beim Terminal-Start nicht erreichbar, aber ein gültiger Snapshot vorhanden → Terminal
     * fährt im Offline-Modus hoch statt in den Fehlerzustand C15).
     */
    public boolean hasUsableSnapshot() {
        SnapshotDto snapshot = this.snapshotStore.get();
        return snapshot != null && !isExpired(snapshot);
    }

    private static boolean isExpired(SnapshotDto snapshot) {
        LocalDateTime expiry = snapshot.generatedAt().plusMinutes(snapshot.offlineMaxDurationMinutes());
        return LocalDateTime.now().isAfter(expiry);
    }

    private static SnapshotUserDto findUser(SnapshotDto snapshot, int userId) {
        for (SnapshotUserDto u : snapshot.users()) {
            if (u.id() == userId) {
                return u;
            }
        }
        return null;
    }

    private static SnapshotDeviceDto findDevice(SnapshotDto snapshot, int deviceId) {
        for (SnapshotDeviceDto d : snapshot.devices()) {
            if (d.id() == deviceId) {
                return d;
            }
        }
        return null;
    }

    private static SnapshotProgramDto findProgram(SnapshotDto snapshot, int programId) {
        for (SnapshotProgramDto p : snapshot.programs()) {
            if (p.id() == programId) {
                return p;
            }
        }
        return null;
    }

    private static SnapshotUserGroupDto findGroup(SnapshotDto snapshot, Integer groupId) {
        if (groupId == null) {
            return null;
        }
        for (SnapshotUserGroupDto g : snapshot.userGroups()) {
            if (g.id() == groupId) {
                return g;
            }
        }
        return null;
    }

    private static ProgramDto toProgramDto(SnapshotProgramDto p, SnapshotUserGroupDto group) {
        BigDecimal priceAtMaxDuration = OfflinePricing.priceAtMaxDuration(p, group);
        return new ProgramDto(p.id(), p.name(), p.type(), p.maxDurationSeconds(), p.freeDurationSeconds(),
                p.flagfall(), p.rate(), p.timeUnit(), p.autoEnd(), p.earliestAutoEndSeconds(), p.enabled(),
                priceAtMaxDuration);
    }

    /**
     * Ob das Journal noch mindestens einen nicht nachgemeldeten Eintrag enthält - kleine
     * Hilfsmethode für Diagnose/Logging.
     */
    public boolean hasPendingJournalEntries() {
        return this.journal.hasPendingEntries();
    }
}
