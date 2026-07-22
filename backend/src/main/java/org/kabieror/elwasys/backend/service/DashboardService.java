package org.kabieror.elwasys.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datenbeschaffung für das Admin-Dashboard (Phase 3 AP3, siehe docs/kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../views/AdminDashboardView#loadData} und
 * {@code AdminDashboardLocationPanel} (Alt-Portal). Liefert je Standort die dort
 * befindlichen Geräte mit ihrem aktuellen Status (Frei/Besetzt, siehe Testfall P20: der
 * Status wird direkt aus der laufenden {@link ExecutionEntity} in der DB abgeleitet, kein
 * Client-Kontakt nötig) sowie ihrer vollständigen Ausführungshistorie (entspricht
 * {@code DataManager#getExecutions(Device)}).
 *
 * <p>Bewusst als eigener, von Vaadin unabhängiger Service statt Datenbeschaffung direkt in
 * der View: so kann dieselbe Abfrage unverändert von den für AP5 geplanten Live-Updates
 * zwischen Sessions (Ersatz der {@code events/}-Listener + des Vaadin-Push aus dem
 * Alt-Portal) wiederverwendet werden, statt eine zweite Implementierung zu brauchen - siehe
 * docs/kb/05-migration-plan.md, Phase-3-Roadmap ("Kein Live-Push nötig ... baue die
 * Datenbeschaffung so, dass AP5 sie wiederverwenden kann").
 *
 * <p>NICHT Teil dieses Service (siehe docs/kb/05-migration-plan.md, Roadmap "Dialoge/Funktionen"):
 * die Wartungsverbindungs-Infos, die das Alt-Dashboard zusätzlich pro Standort zeigte
 * (Verbindungsstatus/IP-Adresse, Log-/Neustart-Menü) - das ist Teil der für AP4 geplanten
 * Fernwartung über den neuen Backend-Kanal, nicht der reinen DB-getriebenen Statusanzeige
 * dieses Arbeitspakets.
 */
@Service
public class DashboardService {

    private final LocationService locationService;
    private final DeviceService deviceService;
    private final ExecutionService executionService;

    public DashboardService(LocationService locationService, DeviceService deviceService,
            ExecutionService executionService) {
        this.locationService = locationService;
        this.deviceService = deviceService;
        this.executionService = executionService;
    }

    /**
     * Liefert alle Standorte mit ihren Geräten und deren aktuellem Status - eine Abfrage für
     * die gesamte Dashboard-Seite.
     */
    @Transactional(readOnly = true)
    public List<LocationStatus> getLocationStatuses() {
        List<LocationStatus> result = new ArrayList<>();
        for (LocationEntity location : this.locationService.findAll()) {
            List<DeviceStatus> devices = new ArrayList<>();
            for (DeviceEntity device : this.deviceService.findByLocation(location)) {
                devices.add(getDeviceStatus(device));
            }
            result.add(new LocationStatus(location, devices));
        }
        return result;
    }

    /**
     * Liefert den aktuellen Status eines einzelnen Geräts - separat aufrufbar, damit ein
     * künftiges Live-Update (AP5) nach einem Ereignis nur das eine betroffene Gerät statt
     * der gesamten Seite neu laden muss.
     */
    @Transactional(readOnly = true)
    public DeviceStatus getDeviceStatus(DeviceEntity device) {
        Optional<ExecutionEntity> runningExecution = this.executionService.getRunningExecution(device);
        Duration remainingTime = runningExecution.map(this::remainingTime).orElse(null);
        List<ExecutionEntity> executions = this.executionService.getExecutions(device);
        return new DeviceStatus(device, runningExecution, remainingTime, executions);
    }

    /**
     * Restzeit der laufenden Ausführung (Höchstdauer des Programms minus bereits verstrichene
     * Zeit, nie negativ) - eine im Alt-Dashboard nicht vorhandene, zusätzliche Information
     * (siehe Klassenkommentar/Aufgabenstellung Phase 3 AP3: "z.B. laufendes Programm, Nutzer,
     * Restzeit"). Reine Ergänzung, keine Verhaltensänderung an Bestehendem.
     */
    private Duration remainingTime(ExecutionEntity execution) {
        ProgramEntity program = execution.getProgram();
        Duration maxDuration = Duration.ofSeconds(program.getMaxDurationSeconds());
        Duration elapsed = Duration.between(execution.getStart(), LocalDateTime.now());
        Duration remaining = maxDuration.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /**
     * Der Status eines Standorts: die dort befindlichen Geräte mit ihrem jeweiligen Status.
     */
    public record LocationStatus(LocationEntity location, List<DeviceStatus> devices) {
    }

    /**
     * Der Status eines einzelnen Geräts. {@code remainingTime} ist {@code null}, wenn keine
     * Ausführung läuft (leeres {@code runningExecution}).
     */
    public record DeviceStatus(DeviceEntity device, Optional<ExecutionEntity> runningExecution,
            Duration remainingTime, List<ExecutionEntity> executions) {

        /**
         * Entspricht der Alt-Dashboard-Anzeige "Frei"/"Besetzt" (Testfall P20).
         */
        public boolean isOccupied() {
            return this.runningExecution.isPresent();
        }
    }
}
