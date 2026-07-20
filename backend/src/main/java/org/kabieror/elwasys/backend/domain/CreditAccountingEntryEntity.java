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
 * Entspricht der Tabelle {@code credit_accounting} (Guthaben-Buchungen, siehe
 * kb/02-data-model.md) sowie {@code org.kabieror.elwasys.common.CreditAccountingEntry} im
 * Alt-Code.
 *
 * <p><b>Buchungen sind unveränderlich</b> (siehe kb/02-data-model.md, DB-Rollen &amp;
 * Rechte: {@code REVOKE UPDATE, DELETE ON credit_accounting FROM elwaportal}): diese Entity
 * bietet daher bewusst keine Setter für bereits gespeicherte Buchungen an, nur den
 * Konstruktor - {@code CreditService} erzeugt ausschließlich neue Einträge, nie Änderungen
 * an bestehenden.
 *
 * <p>Die Spalte {@code date} hat in der DB einen Default ({@code CURRENT_TIMESTAMP}); der
 * Alt-Code (Common {@code User#payExecution}/{@code #inpayment}/{@code #payout}) setzt sie
 * beim INSERT nie explizit und verlässt sich auf diesen DB-Default. Diese Entity setzt
 * {@code date} stattdessen explizit auf den Anwendungszeitpunkt (siehe
 * {@code CreditService}) - das ist eine bewusste Vereinfachung (siehe
 * kb/05-migration-plan.md, "Entscheidungen"), keine Verhaltensänderung: beide Varianten
 * bedeuten "Zeitpunkt der Buchung", ein Unterschied entstünde nur bei nennenswertem
 * Uhren-Versatz zwischen Anwendungs- und DB-Host.
 */
@Entity
@Table(name = "credit_accounting")
public class CreditAccountingEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * EAGER, siehe {@code UserEntity#getGroup()}: der Alt-Code
     * ({@code CreditAccountingEntry}) trägt immer ein bereits aufgelöstes {@code User}.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "execution_id")
    private ExecutionEntity execution;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column
    private String description;

    protected CreditAccountingEntryEntity() {
        // for JPA
    }

    public CreditAccountingEntryEntity(UserEntity user, ExecutionEntity execution, BigDecimal amount,
            LocalDateTime date, String description) {
        this.user = user;
        this.execution = execution;
        this.amount = amount;
        this.date = date;
        this.description = description;
    }

    public Integer getId() {
        return this.id;
    }

    public UserEntity getUser() {
        return this.user;
    }

    public ExecutionEntity getExecution() {
        return this.execution;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public LocalDateTime getDate() {
        return this.date;
    }

    public String getDescription() {
        return this.description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CreditAccountingEntryEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
