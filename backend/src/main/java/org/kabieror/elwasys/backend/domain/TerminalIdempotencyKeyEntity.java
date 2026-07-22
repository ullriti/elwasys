package org.kabieror.elwasys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entspricht der Tabelle {@code terminal_idempotency_keys} (Phase 4 AP3, siehe
 * docs/kb/02-data-model.md, additive Migration {@code V4__create_terminal_idempotency_keys.sql}).
 * Speichert die Antwort eines terminal-gemeldeten Execution-Ereignisses (Start/Ende/
 * Abbruch/Reset) unter dessen Idempotenz-Schlüssel, damit eine wiederholte Meldung (z.B.
 * nach einem Verbindungsabbruch vor Erhalt der ursprünglichen Antwort) dedupliziert werden
 * kann - siehe {@link org.kabieror.elwasys.backend.api.idempotency.IdempotencyService}.
 */
@Entity
@Table(name = "terminal_idempotency_keys")
public class TerminalIdempotencyKeyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private LocationEntity location;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Lob
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected TerminalIdempotencyKeyEntity() {
        // for JPA
    }

    public TerminalIdempotencyKeyEntity(String idempotencyKey, LocationEntity location, String operation,
            int responseStatus, String responseBody) {
        this.idempotencyKey = idempotencyKey;
        this.location = location;
        this.operation = operation;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public Long getId() {
        return this.id;
    }

    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    public LocationEntity getLocation() {
        return this.location;
    }

    public String getOperation() {
        return this.operation;
    }

    public int getResponseStatus() {
        return this.responseStatus;
    }

    public String getResponseBody() {
        return this.responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TerminalIdempotencyKeyEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
