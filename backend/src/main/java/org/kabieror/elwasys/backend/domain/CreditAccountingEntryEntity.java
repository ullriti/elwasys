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
 * docs/kb/02-data-model.md) sowie {@code org.kabieror.elwasys.common.CreditAccountingEntry} im
 * Alt-Code.
 *
 * <p><b>Buchungen sind unveränderlich</b> (siehe docs/kb/02-data-model.md, DB-Rollen &amp;
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
 * docs/kb/05-migration-plan.md, "Entscheidungen"), keine Verhaltensänderung: beide Varianten
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

    /**
     * <b>LAZY, nicht EAGER</b> (Befund aus dem Cutover 2026-09-20). Mit EAGER lud Hibernate
     * beim Abruf der Buchungshistorie fuer JEDE Ergebniszeile eine eigene Einzelabfrage nach
     * ({@code EntitySelectFetchInitializer -> SingleIdLoadPlan.load}) - die abgeleitete
     * Methode {@code findByUser_IdOrderByDateDescIdDesc} erzeugt ja nur ein einfaches
     * SELECT ohne JOIN. Auf echten Bestandsdaten (ein Konto mit 506 Buchungen) brauchte das
     * Benutzer-Dashboard dadurch rund 20 Minuten; in {@code pg_stat_activity} war nichts zu
     * sehen, weil jede Einzelabfrage millisekundenschnell ist - es sind nur Hunderte.
     *
     * <p>LAZY statt {@code @EntityGraph} bewusst gewaehlt: die Verknuepfung wird nirgends
     * gelesen (kein einziger Aufruf von {@link #getExecution()} im Haupt- oder Testcode),
     * beide Ansichten der Buchungshistorie zeigen nur Datum, Betrag und Buchungstext. Ein
     * JOIN wuerde die Daten also weiterhin laden, nur schneller - hier werden sie gar nicht
     * gebraucht. Wer den Bezug kuenftig doch anzeigt, holt ihn gezielt per
     * {@code @EntityGraph(attributePaths = "execution")} auf der jeweiligen Abfrage.
     */
    @ManyToOne(fetch = FetchType.LAZY)
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
