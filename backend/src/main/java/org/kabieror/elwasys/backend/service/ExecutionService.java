package org.kabieror.elwasys.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.events.ExecutionChangedEvent;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1-Portierung der Persistenz-seitigen Execution-Lebenszyklus-Logik aus
 * {@code org.kabieror.elwasys.common.Execution} (Start/Stop/Reset/Preis/Ablauf) sowie der
 * Datenbank-Anteile von {@code ExecutionManager}/{@code ExecutionFinisher} im Client (siehe
 * docs/kb/05-migration-plan.md, AP2). Hardwarenahe Teile (Leistungsmessung, Ein-/Ausschalten der
 * Steckdose, automatisches Beenden per Leistungsmessung, Email-/Pushover-Benachrichtigungen)
 * bleiben laut Zielarchitektur im Terminal und sind hier bewusst NICHT nachgebildet.
 */
@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final PricingService pricingService;
    private final CreditService creditService;
    private final ApplicationEventPublisher eventPublisher;

    public ExecutionService(ExecutionRepository executionRepository, PricingService pricingService,
            CreditService creditService, ApplicationEventPublisher eventPublisher) {
        this.executionRepository = executionRepository;
        this.pricingService = pricingService;
        this.creditService = creditService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Veröffentlicht ein {@link ExecutionChangedEvent} für die gegebene Ausführung (Phase 3
     * AP5, siehe docs/kb/05-migration-plan.md) - gemeinsame Hilfsmethode für alle
     * lebenszyklus-verändernden Methoden dieser Klasse. Bewusst NICHT in {@link
     * #stopExecution}, weil diese Methode ausschließlich intern von {@link #finishExecution}
     * aufgerufen wird (siehe deren Javadoc) - ein zweites Ereignis für denselben Aufruf wäre
     * redundant.
     */
    private void publishChanged(ExecutionEntity execution) {
        Integer deviceId = execution.getDevice() == null ? null : execution.getDevice().getId();
        Integer userId = execution.getUser() == null ? null : execution.getUser().getId();
        this.eventPublisher.publishEvent(new ExecutionChangedEvent(execution.getId(), deviceId, userId));
    }

    /**
     * 1:1-Portierung von {@code DataManager#newExecution}/dem Insert-Konstruktor
     * {@code Execution(DataManager, Device, Program, User)}: legt eine neue Ausführung an,
     * ohne sie zu starten ({@code start}/{@code stop} bleiben {@code null},
     * {@code finished=false}).
     */
    @Transactional
    public ExecutionEntity createExecution(DeviceEntity device, ProgramEntity program, UserEntity user) {
        ExecutionEntity execution = this.executionRepository.save(new ExecutionEntity(device, program, user));
        publishChanged(execution);
        return execution;
    }

    /**
     * 1:1-Portierung von {@code Execution#start()}: setzt die Startzeit nur beim ersten
     * Aufruf (ein bereits gestartetes Execution bleibt unverändert - kein erneutes
     * Schreiben).
     */
    @Transactional
    public ExecutionEntity startExecution(ExecutionEntity execution) {
        return startExecution(execution, null);
    }

    /**
     * Wie {@link #startExecution(ExecutionEntity)}, aber mit einem vom Terminal
     * mitgelieferten Original-Zeitstempel (AP3, Phase 4, additiv - siehe
     * {@code ExecutionStartRequest#clientTimestamp()}). Ist {@code clientTimestamp}
     * {@code null} (der bisherige, einzige Aufrufpfad), verhält sich diese Methode exakt wie
     * zuvor ({@code LocalDateTime.now()}).
     */
    @Transactional
    public ExecutionEntity startExecution(ExecutionEntity execution, LocalDateTime clientTimestamp) {
        if (execution.getStart() != null) {
            return execution;
        }
        execution.setStart(clientTimestamp != null ? clientTimestamp : LocalDateTime.now());
        execution = this.executionRepository.save(execution);
        publishChanged(execution);
        return execution;
    }

    /**
     * 1:1-Portierung von {@code Execution#stop()}: markiert die Ausführung als
     * abgeschlossen und setzt die Endzeit. Anders als {@link #startExecution}, OHNE Schutz
     * gegen Mehrfachaufruf (der Alt-Code schreibt bei jedem Aufruf erneut) - Verhalten
     * bewahren.
     */
    @Transactional
    public ExecutionEntity stopExecution(ExecutionEntity execution) {
        return stopExecution(execution, null);
    }

    /**
     * Wie {@link #stopExecution(ExecutionEntity)}, aber mit einem vom Terminal
     * mitgelieferten Original-Zeitstempel (AP3, Phase 4, additiv - siehe
     * {@code ExecutionEndRequest#clientTimestamp()}).
     */
    @Transactional
    public ExecutionEntity stopExecution(ExecutionEntity execution, LocalDateTime clientTimestamp) {
        execution.setFinished(true);
        LocalDateTime stop = clientTimestamp != null ? clientTimestamp : LocalDateTime.now();
        // Issue #18: Der Stop-Zeitstempel darf nie VOR dem Start liegen. Das kann nur bei einer
        // stark verspäteten Offline-Nachmeldung auftreten, deren Start-Zeitstempel unabhängig
        // vom Stop auf die Serverzeit ersetzt wurde (ClientTimestampPolicy prüft beide
        // Zeitstempel je gegen "jetzt", nie gegeneinander) - dann entstünde sonst eine negative
        // Dauer (0-€-Waschgang) UND ein Datensatz mit stop < start in der DB. Der reale
        // End-Zeitpunkt bleibt in allen anderen Fällen unverändert als Audit-Record erhalten;
        // die Preis-Obergrenze (start + maxDuration) erzwingt {@link #getPrice} separat.
        LocalDateTime start = execution.getStart();
        if (start != null && stop.isBefore(start)) {
            stop = start;
        }
        execution.setStop(stop);
        return this.executionRepository.save(execution);
    }

    /**
     * Beendet eine Ausführung UND bucht ihren Preis ab - entspricht dem
     * persistenz-relevanten Teil von {@code ExecutionFinisher#executeAction()} im Client
     * (dort in dieser Reihenfolge: Strom aus [hardwarenah, hier nicht Teil des Backends],
     * {@code e.stop()}, {@code e.getUser().payExecution(e)}). Wird sowohl für ein reguläres
     * Ende als auch für einen Abbruch verwendet - persistenzseitig ist das identisch, der
     * einzige Unterschied (Abbruch-Benachrichtigungstext) ist hardwarenah/UI-seitig und
     * bleibt im Terminal.
     */
    @Transactional
    public ExecutionEntity finishExecution(ExecutionEntity execution) {
        return finishExecution(execution, null);
    }

    /**
     * Wie {@link #finishExecution(ExecutionEntity)}, aber mit einem vom Terminal
     * mitgelieferten Original-Zeitstempel (AP3, Phase 4, additiv).
     */
    @Transactional
    public ExecutionEntity finishExecution(ExecutionEntity execution, LocalDateTime clientTimestamp) {
        ExecutionEntity stopped = stopExecution(execution, clientTimestamp);
        BigDecimal price = getPrice(stopped);
        this.creditService.payExecution(stopped, price);
        publishChanged(stopped);
        return stopped;
    }

    /**
     * Lädt eine Ausführung FRISCH und pessimistisch GESPERRT ({@code SELECT ... FOR UPDATE},
     * Issue #20 - AP3, siehe
     * {@link org.kabieror.elwasys.backend.repository.ExecutionRepository#findWithLockById}).
     * Der Beenden-/Abbruch-Pfad ({@code ExecutionController#finishOrAbort}) prüft den "bereits
     * beendet"-Wächter damit auf der gesperrten Zeile innerhalb der Idempotenz-Transaktion,
     * statt auf einer zuvor detacht geladenen Instanz - so kann ein zweiter, paralleler
     * {@code finish} nicht denselben {@code finished == false}-Zustand sehen und doppelt
     * abrechnen. MUSS innerhalb der Transaktion aufgerufen werden, in der anschließend beendet
     * wird.
     *
     * @throws IllegalStateException wenn die Ausführung nicht (mehr) existiert - für den
     *         Aufrufer eine Invariante, da der Standort-Scope-Wächter dieselbe Id unmittelbar
     *         zuvor erfolgreich aufgelöst hat
     */
    @Transactional
    public ExecutionEntity getForUpdate(Integer id) {
        return this.executionRepository.findWithLockById(id)
                .orElseThrow(() -> new IllegalStateException("Ausführung id=" + id + " nicht gefunden."));
    }

    /**
     * 1:1-Portierung von {@code Execution#reset()}.
     *
     * <p><b>Beobachtung</b> (siehe docs/kb/05-migration-plan.md): der Alt-Code setzt
     * {@code finished} beim Zurücksetzen auf {@code TRUE}, nicht auf {@code FALSE} - trotz
     * des Methodennamens "reset" wird die Ausführung damit als abgeschlossen markiert
     * (ohne je gestartet/beendet worden zu sein). Das ist beabsichtigt: {@code reset()}
     * wird im Client nur aufgerufen, wenn das Einschalten der Steckdose nach dem Anlegen
     * der Ausführung fehlschlägt ({@code ExecutionManager#startExecution}) - die Ausführung
     * soll dann NICHT als "noch laufend/zu bezahlen" gelten (siehe
     * {@code User#loadCredit}/{@code hasExpiredExecutions}, die beide auf
     * {@code finished=false} prüfen). Dieses Verhalten wird hier bewusst 1:1 übernommen.
     */
    @Transactional
    public ExecutionEntity resetExecution(ExecutionEntity execution) {
        execution.setStart(null);
        execution.setStop(null);
        execution.setFinished(true);
        execution = this.executionRepository.save(execution);
        publishChanged(execution);
        return execution;
    }

    /**
     * 1:1-Portierung von {@code Execution#getPrice()}.
     */
    public BigDecimal getPrice(ExecutionEntity execution) {
        if (execution.getStart() == null) {
            return BigDecimal.ZERO;
        }
        ProgramEntity program = execution.getProgram();
        Duration maxDuration = Duration.ofSeconds(program.getMaxDurationSeconds());
        if (execution.isFinished()) {
            if (execution.getStop() == null) {
                return this.pricingService.getPrice(program, maxDuration, execution.getUser());
            }
            // Issue #18: Abrechnungsdauer auf [0, maxDuration] deckeln. Ein vom Terminal
            // gemeldeter Stop-Zeitstempel kann - trotz Start-Klemmung in stopExecution - länger
            // als die Maximaldauer zurückliegen (langer Backend-Ausfall während eines online
            // gestarteten Waschgangs, Szenario 1: Überberechnung). Ohne Deckel würde ein
            // DYNAMIC-Programm die gesamte Ausfallzeit mitberechnen, ungedeckelt über den beim
            // Start geprüften maxPrice hinaus. Für den Normalfall (Dauer im Fenster) ändert sich
            // nichts. Eine negative Dauer (Alt-Bestand vor der stopExecution-Klemmung) wird als
            // 0 gewertet statt als negativer Rechenfaktor.
            Duration billed = Duration.between(execution.getStart(), execution.getStop());
            if (billed.isNegative()) {
                billed = Duration.ZERO;
            } else if (billed.compareTo(maxDuration) > 0) {
                billed = maxDuration;
            }
            return this.pricingService.getPrice(program, billed, execution.getUser());
        } else {
            Duration timeSinceStart = Duration.between(execution.getStart(), LocalDateTime.now());
            if (timeSinceStart.compareTo(maxDuration) > 0) {
                // Maximaldauer überschritten
                return this.pricingService.getPrice(program, maxDuration, execution.getUser());
            } else {
                return this.pricingService.getPrice(program, timeSinceStart, execution.getUser());
            }
        }
    }

    /**
     * 1:1-Portierung von {@code Execution#isExpired()}: die Höchstdauer des Programms ist
     * abgelaufen, die Ausführung aber noch nicht verrechnet.
     */
    public boolean isExpired(ExecutionEntity execution) {
        if (execution.getStart() == null || execution.isFinished()) {
            return false;
        }
        Duration maxDuration = Duration.ofSeconds(execution.getProgram().getMaxDurationSeconds());
        return Duration.between(execution.getStart(), LocalDateTime.now()).compareTo(maxDuration) > 0;
    }

    /**
     * 1:1-Portierung von {@code DataManager#getNotFinishedExecutions}.
     */
    @Transactional(readOnly = true)
    public List<ExecutionEntity> getNotFinishedExecutions(UserEntity user) {
        return this.executionRepository.findByUser_IdAndFinishedFalseAndStartIsNotNull(user.getId());
    }

    /**
     * 1:1-Portierung von {@code User#hasExpiredExecutions}.
     */
    @Transactional(readOnly = true)
    public boolean hasExpiredExecutions(UserEntity user) {
        return getNotFinishedExecutions(user).stream().anyMatch(this::isExpired);
    }

    /**
     * 1:1-Portierung des Filters im Konstruktor von
     * {@code Portal/.../components/ExpiredExecutionsWindow} (Alt-Portal, Phase 3 AP4, siehe
     * docs/kb/05-migration-plan.md): alle nicht abgerechneten Ausführungen eines Benutzers,
     * eingeschränkt auf tatsächlich abgelaufene (die reine "läuft noch, ist aber noch nicht
     * abgelaufen"-Menge aus {@link #getNotFinishedExecutions} wird dort zusätzlich
     * herausgefiltert).
     */
    @Transactional(readOnly = true)
    public List<ExecutionEntity> getExpiredExecutions(UserEntity user) {
        return getNotFinishedExecutions(user).stream().filter(this::isExpired).toList();
    }

    /**
     * 1:1-Portierung von {@code common.Execution#delete()} (dort zusätzlich gegen
     * Mehrfachaufruf/virtuelle Ausführungen [{@code id < 0}] geschützt - beides in der Praxis
     * nicht erreichbar für Ausführungen, die über {@link #getExpiredExecutions} aufgelistet
     * wurden, siehe {@code ExpiredExecutionsWindow}s "Löschen"-Knopf).
     */
    @Transactional
    public void delete(ExecutionEntity execution) {
        Integer executionId = execution.getId();
        Integer deviceId = execution.getDevice() == null ? null : execution.getDevice().getId();
        Integer userId = execution.getUser() == null ? null : execution.getUser().getId();
        this.executionRepository.delete(execution);
        this.eventPublisher.publishEvent(new ExecutionChangedEvent(executionId, deviceId, userId));
    }

    /**
     * 1:1-Portierung von {@code DataManager#getRunningExecution(Device)}: die laufende,
     * NICHT abgelaufene Ausführung eines Geräts (abgelaufene Ausführungen werden wie im
     * Alt-Code übersprungen, nicht als "laufend" zurückgegeben).
     */
    @Transactional(readOnly = true)
    public Optional<ExecutionEntity> getRunningExecution(DeviceEntity device) {
        return this.executionRepository.findByDevice_IdAndFinishedFalseAndStartIsNotNull(device.getId()).stream()
                .filter(e -> !isExpired(e)).findFirst();
    }

    /**
     * 1:1-Portierung von {@code DataManager#getExecutions(Device)}.
     */
    @Transactional(readOnly = true)
    public List<ExecutionEntity> getExecutions(DeviceEntity device) {
        return this.executionRepository.findByDevice_IdAndStartIsNotNullOrderByStartDesc(device.getId());
    }

    /**
     * 1:1-Portierung von {@code DataManager#getLastUser(Device)}: der Benutzer der letzten
     * gestarteten Ausführung eines Geräts (nur echte, keine virtuellen Benutzer mit
     * negativer Id).
     */
    @Transactional(readOnly = true)
    public Optional<UserEntity> getLastUser(DeviceEntity device) {
        return this.executionRepository
                .findFirstByDevice_IdAndUser_IdGreaterThanEqualAndStartIsNotNullOrderByIdDesc(device.getId(), 0)
                .map(ExecutionEntity::getUser);
    }
}
