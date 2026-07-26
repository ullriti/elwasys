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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entspricht der Tabelle {@code terminal_offline_incidents} (Issue #89, additive Migration
 * {@code V12__create_terminal_offline_incidents.sql}): ein vom Terminal gemeldeter Vorfall des
 * Offline-Pfads, der bisher nur im lokalen Pi-Log sichtbar war.
 *
 * <p>Fachlich sind das zwei Fälle (siehe {@link #KIND_DEAD_LETTER}/{@link #KIND_GHOST_EXECUTION}),
 * die beide **potenziellen Geldverlust** bedeuten und deshalb einen Menschen erreichen müssen:
 * offene (nicht quittierte) Vorfälle ziehen den
 * {@code OfflineIncidentHealthIndicator} und damit {@code /actuator/health/operational} auf
 * {@code OUT_OF_SERVICE} (Alerting, siehe deploy/monitoring/).
 *
 * <p>{@link #getIncidentKey()} ist der Idempotenz-Anker: dieselbe Meldung darf nach einem
 * Reconnect/Terminal-Neustart erneut eintreffen, ohne einen Doppel-Eintrag zu erzeugen (Muster
 * wie {@link TerminalIdempotencyKeyEntity}).
 */
@Entity
@Table(name = "terminal_offline_incidents")
public class TerminalOfflineIncidentEntity {

    /**
     * Ein Journal-Eintrag wurde dauerhaft fachlich abgelehnt und in die Dead-Letter-Datei
     * verschoben - die Buchung ist damit endgültig NICHT beim Backend angekommen.
     */
    public static final String KIND_DEAD_LETTER = "DEAD_LETTER";

    /**
     * Beim Replay entstand eine Geister-Ausführung (START angelegt, Terminierung fachlich
     * abgelehnt); der kompensierende Abbruch schlug fehl - die Ausführung bleibt offen.
     */
    public static final String KIND_GHOST_EXECUTION = "GHOST_EXECUTION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_key", nullable = false, unique = true, length = 120)
    private String incidentKey;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private LocationEntity location;

    @Column(nullable = false, length = 40)
    private String kind;

    /** Art des betroffenen Journal-Eintrags (START/FINISH/ABORT), sofern bekannt. */
    @Column(name = "entry_type", length = 20)
    private String entryType;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** Informativ: wessen Buchung betroffen war (bleibt bei Nutzerlöschung als NULL erhalten). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    /** Der lokal berechnete Betrag der verlorenen Buchung - der eigentliche Schaden. */
    @Column(name = "charged_price", precision = 10, scale = 2)
    private BigDecimal chargedPrice;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    /** Zeitpunkt auf dem Terminal (Client-Uhr), sofern gemeldet. */
    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "reported_at", nullable = false)
    private LocalDateTime reportedAt = LocalDateTime.now();

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    protected TerminalOfflineIncidentEntity() {
        // for JPA
    }

    public TerminalOfflineIncidentEntity(String incidentKey, LocationEntity location, String kind, String entryType,
            String idempotencyKey, UserEntity user, BigDecimal chargedPrice, String reason, LocalDateTime occurredAt) {
        this.incidentKey = incidentKey;
        this.location = location;
        this.kind = kind;
        this.entryType = entryType;
        this.idempotencyKey = idempotencyKey;
        this.user = user;
        this.chargedPrice = chargedPrice;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    /**
     * Quittiert den Vorfall (Portal-Aktion): beendet den Alarm, lässt den Beleg aber bestehen.
     * Bewusst idempotent - eine zweite Quittierung überschreibt die erste nicht.
     */
    public void acknowledge(String username) {
        if (this.acknowledgedAt == null) {
            this.acknowledgedAt = LocalDateTime.now();
            this.acknowledgedBy = username;
        }
    }

    public boolean isAcknowledged() {
        return this.acknowledgedAt != null;
    }

    public Long getId() {
        return this.id;
    }

    public String getIncidentKey() {
        return this.incidentKey;
    }

    public LocationEntity getLocation() {
        return this.location;
    }

    public String getKind() {
        return this.kind;
    }

    public String getEntryType() {
        return this.entryType;
    }

    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    public UserEntity getUser() {
        return this.user;
    }

    public BigDecimal getChargedPrice() {
        return this.chargedPrice;
    }

    public String getReason() {
        return this.reason;
    }

    public LocalDateTime getOccurredAt() {
        return this.occurredAt;
    }

    public LocalDateTime getReportedAt() {
        return this.reportedAt;
    }

    public LocalDateTime getAcknowledgedAt() {
        return this.acknowledgedAt;
    }

    public String getAcknowledgedBy() {
        return this.acknowledgedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TerminalOfflineIncidentEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
