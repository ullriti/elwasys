package org.kabieror.elwasys.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalOfflineIncidentEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.TerminalOfflineIncidentRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nimmt die vom Terminal gemeldeten Offline-Vorfälle entgegen und verwaltet ihre Quittierung
 * (Issue #89, siehe {@link TerminalOfflineIncidentEntity}).
 *
 * <p>Hintergrund (finale Review R5): Dead-Letter- und Geister-Execution-Fälle landeten bisher
 * ausschließlich als {@code logger.error} im lokalen Log des Raspberry Pi - ein
 * dead-gelettert Journal-Eintrag ist aber eine **verlorene Offline-Buchung (Geld)**. Über den
 * bestehenden Wartungs-WebSocket meldet das Terminal solche Vorfälle jetzt hierher; offene
 * (nicht quittierte) Vorfälle heben den {@code OfflineIncidentHealthIndicator} und damit den
 * Alerting-Endpunkt {@code /actuator/health/operational}.
 */
@Service
public class TerminalOfflineIncidentService {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalOfflineIncidentService.class);

    private final TerminalOfflineIncidentRepository incidentRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    public TerminalOfflineIncidentService(TerminalOfflineIncidentRepository incidentRepository,
            LocationRepository locationRepository, UserRepository userRepository) {
        this.incidentRepository = incidentRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Meldet einen Vorfall - idempotent über {@code incidentKey}: dieselbe Meldung darf nach
     * einem Reconnect oder einem Terminal-Neustart erneut eintreffen (das Terminal kennt den
     * Zustellstatus nicht), ohne einen Doppel-Eintrag zu erzeugen. Ein bereits quittierter
     * Vorfall wird durch eine erneute Meldung bewusst NICHT wieder geöffnet - sonst könnte ein
     * Terminal, das seine Dead-Letter-Datei behält, den Alarm dauerhaft wiederbeleben.
     *
     * @return der gespeicherte (oder bereits vorhandene) Vorfall
     */
    @Transactional
    public TerminalOfflineIncidentEntity report(Integer locationId, String incidentKey, String kind, String entryType,
            String idempotencyKey, Integer userId, BigDecimal chargedPrice, String reason, LocalDateTime occurredAt) {
        Optional<TerminalOfflineIncidentEntity> existing = this.incidentRepository.findByIncidentKey(incidentKey);
        if (existing.isPresent()) {
            LOG.debug("Offline-Vorfall '{}' war bereits gemeldet - ignoriere die Wiederholung.", incidentKey);
            return existing.get();
        }

        LocationEntity location = this.locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Standort: " + locationId));
        // Der Nutzer ist rein informativ: ist er zwischenzeitlich gelöscht, soll der Vorfall
        // trotzdem festgehalten werden (der Geldverlust bleibt ja bestehen).
        UserEntity user = userId == null ? null : this.userRepository.findById(userId).orElse(null);

        TerminalOfflineIncidentEntity incident = new TerminalOfflineIncidentEntity(incidentKey, location, kind,
                entryType, idempotencyKey, user, chargedPrice, reason, occurredAt);
        TerminalOfflineIncidentEntity saved = this.incidentRepository.save(incident);
        LOG.error("Offline-Vorfall am Standort '{}' gemeldet ({}): {} - Betrag {}, Eintrag {}/{}. Bitte im Portal "
                        + "sichten und quittieren.", location.getName(), kind, reason, chargedPrice, entryType,
                idempotencyKey);
        return saved;
    }

    /** Offene (nicht quittierte) Vorfälle, neueste zuerst - für die Portal-Ansicht. */
    @Transactional(readOnly = true)
    public List<TerminalOfflineIncidentEntity> findOpen() {
        return this.incidentRepository.findByAcknowledgedAtIsNullOrderByReportedAtDesc();
    }

    /** Anzahl offener Vorfälle - für den Health-Indicator (keine Entity-Ladung nötig). */
    @Transactional(readOnly = true)
    public long countOpen() {
        return this.incidentRepository.countByAcknowledgedAtIsNull();
    }

    @Transactional(readOnly = true)
    public List<TerminalOfflineIncidentEntity> findAll() {
        return this.incidentRepository.findAll();
    }

    /**
     * Quittiert einen Vorfall (Portal-Aktion): beendet den Alarm, der Beleg bleibt erhalten.
     * Eine erneute Quittierung ist wirkungslos (siehe
     * {@link TerminalOfflineIncidentEntity#acknowledge(String)}).
     */
    @Transactional
    public void acknowledge(Long incidentId, String username) {
        TerminalOfflineIncidentEntity incident = this.incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Vorfall: " + incidentId));
        incident.acknowledge(username);
        this.incidentRepository.save(incident);
        LOG.info("Offline-Vorfall {} ('{}') von '{}' quittiert.", incidentId, incident.getIncidentKey(), username);
    }
}
