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
import java.time.LocalDateTime;

/**
 * Entspricht der Tabelle {@code terminal_tokens} (Phase 2 AP4, siehe docs/kb/02-data-model.md,
 * additive Migration {@code V3__create_terminal_tokens.sql}). Ein Standort-Token
 * authentifiziert ein Raspi-Terminal gegenüber der REST-API ({@code /api/v1/**}) und dem
 * WebSocket-Endpunkt, siehe {@link org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService}.
 *
 * <p>Nur der Hash des Tokens wird gespeichert ({@link #getTokenHash()}), niemals der
 * Klartext - siehe {@code TerminalTokenService#createToken}. Mehrere aktive Tokens pro
 * Standort sind zulässig (n:1 zu {@link LocationEntity}), das ermöglicht Rotation ohne
 * Ausfallzeit: ein neues Token anlegen, das Terminal umstellen, dann das alte per
 * {@link #revoke()} deaktivieren.
 */
@Entity
@Table(name = "terminal_tokens")
public class TerminalTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private LocationEntity location;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(length = 100)
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    protected TerminalTokenEntity() {
        // for JPA
    }

    public TerminalTokenEntity(LocationEntity location, String tokenHash, String label) {
        this.location = location;
        this.tokenHash = tokenHash;
        this.label = label;
    }

    public Integer getId() {
        return this.id;
    }

    public LocationEntity getLocation() {
        return this.location;
    }

    public String getTokenHash() {
        return this.tokenHash;
    }

    public String getLabel() {
        return this.label;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public LocalDateTime getRevokedAt() {
        return this.revokedAt;
    }

    public boolean isActive() {
        return this.revokedAt == null;
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getLastUsedAt() {
        return this.lastUsedAt;
    }

    public void touchLastUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /**
     * Setzt {@code last_used_at} auf einen expliziten Zeitpunkt (Issue #45, Pre-Launch AP4) -
     * erlaubt {@code TerminalTokenService}, eine injizierte Zeitquelle zu verwenden und den
     * Schreibvorgang zu drosseln (siehe dort).
     */
    public void touchLastUsed(LocalDateTime timestamp) {
        this.lastUsedAt = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TerminalTokenEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
