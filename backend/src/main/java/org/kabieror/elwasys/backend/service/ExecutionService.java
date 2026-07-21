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
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1-Portierung der Persistenz-seitigen Execution-Lebenszyklus-Logik aus
 * {@code org.kabieror.elwasys.common.Execution} (Start/Stop/Reset/Preis/Ablauf) sowie der
 * Datenbank-Anteile von {@code ExecutionManager}/{@code ExecutionFinisher} im Client (siehe
 * kb/05-migration-plan.md, AP2). Hardwarenahe Teile (Leistungsmessung, Ein-/Ausschalten der
 * Steckdose, automatisches Beenden per Leistungsmessung, Email-/Pushover-Benachrichtigungen)
 * bleiben laut Zielarchitektur im Terminal und sind hier bewusst NICHT nachgebildet.
 */
@Service
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final PricingService pricingService;
    private final CreditService creditService;

    public ExecutionService(ExecutionRepository executionRepository, PricingService pricingService,
            CreditService creditService) {
        this.executionRepository = executionRepository;
        this.pricingService = pricingService;
        this.creditService = creditService;
    }

    /**
     * 1:1-Portierung von {@code DataManager#newExecution}/dem Insert-Konstruktor
     * {@code Execution(DataManager, Device, Program, User)}: legt eine neue Ausführung an,
     * ohne sie zu starten ({@code start}/{@code stop} bleiben {@code null},
     * {@code finished=false}).
     */
    @Transactional
    public ExecutionEntity createExecution(DeviceEntity device, ProgramEntity program, UserEntity user) {
        return this.executionRepository.save(new ExecutionEntity(device, program, user));
    }

    /**
     * 1:1-Portierung von {@code Execution#start()}: setzt die Startzeit nur beim ersten
     * Aufruf (ein bereits gestartetes Execution bleibt unverändert - kein erneutes
     * Schreiben).
     */
    @Transactional
    public ExecutionEntity startExecution(ExecutionEntity execution) {
        if (execution.getStart() != null) {
            return execution;
        }
        execution.setStart(LocalDateTime.now());
        return this.executionRepository.save(execution);
    }

    /**
     * 1:1-Portierung von {@code Execution#stop()}: markiert die Ausführung als
     * abgeschlossen und setzt die Endzeit. Anders als {@link #startExecution}, OHNE Schutz
     * gegen Mehrfachaufruf (der Alt-Code schreibt bei jedem Aufruf erneut) - Verhalten
     * bewahren.
     */
    @Transactional
    public ExecutionEntity stopExecution(ExecutionEntity execution) {
        execution.setFinished(true);
        execution.setStop(LocalDateTime.now());
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
        ExecutionEntity stopped = stopExecution(execution);
        BigDecimal price = getPrice(stopped);
        this.creditService.payExecution(stopped, price);
        return stopped;
    }

    /**
     * 1:1-Portierung von {@code Execution#reset()}.
     *
     * <p><b>Beobachtung</b> (siehe kb/05-migration-plan.md): der Alt-Code setzt
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
        return this.executionRepository.save(execution);
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
            return this.pricingService.getPrice(program, Duration.between(execution.getStart(), execution.getStop()),
                    execution.getUser());
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
     * kb/05-migration-plan.md): alle nicht abgerechneten Ausführungen eines Benutzers,
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
        this.executionRepository.delete(execution);
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
