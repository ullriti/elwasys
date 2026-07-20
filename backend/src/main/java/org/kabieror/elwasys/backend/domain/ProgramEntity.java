package org.kabieror.elwasys.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entspricht der Tabelle {@code programs} (Waschprogramm/Tarif, siehe kb/02-data-model.md)
 * sowie {@code org.kabieror.elwasys.common.Program} im Alt-Code. Die eigentliche
 * Preisberechnung (inkl. Rabatt/Rundungsverhalten) liegt bewusst NICHT auf der Entity,
 * sondern in {@code PricingService} (1:1-Portierung von {@code Program.getPrice}) - siehe
 * kb/05-migration-plan.md.
 */
@Entity
@Table(name = "programs")
public class ProgramEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "PROGRAM_TYPE")
    private ProgramType type;

    /**
     * Maximale Programmdauer in Sekunden (Spalte {@code max_duration}, INTEGER).
     */
    @Column(name = "max_duration", nullable = false)
    private int maxDurationSeconds;

    /**
     * Dauer, bis zu der ein Abbruch kostenlos ist, in Sekunden (Spalte
     * {@code free_duration}).
     */
    @Column(name = "free_duration", nullable = false)
    private int freeDurationSeconds;

    @Column
    private BigDecimal flagfall;

    @Column
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "time_unit", columnDefinition = "TIME_UNIT_TYPE")
    private TimeUnitType timeUnit;

    @Column(name = "auto_end", nullable = false)
    private boolean autoEnd = true;

    /**
     * Zeit ab Programmstart, in der nicht automatisch beendet werden soll, in Sekunden
     * (Spalte {@code earliest_auto_end}).
     */
    @Column(name = "earliest_auto_end", nullable = false)
    private int earliestAutoEndSeconds;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * n:m-Tabelle {@code programs_valid_user_groups}. EAGER, siehe
     * {@code DeviceEntity#getLocation()}.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "programs_valid_user_groups",
            joinColumns = @JoinColumn(name = "program_id"),
            inverseJoinColumns = @JoinColumn(name = "group_id"))
    private Set<UserGroupEntity> validUserGroups = new HashSet<>();

    protected ProgramEntity() {
        // for JPA
    }

    public ProgramEntity(String name, ProgramType type, int maxDurationSeconds) {
        this.name = name;
        this.type = type;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public Integer getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProgramType getType() {
        return this.type;
    }

    public void setType(ProgramType type) {
        this.type = type;
    }

    public int getMaxDurationSeconds() {
        return this.maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public int getFreeDurationSeconds() {
        return this.freeDurationSeconds;
    }

    public void setFreeDurationSeconds(int freeDurationSeconds) {
        this.freeDurationSeconds = freeDurationSeconds;
    }

    public BigDecimal getFlagfall() {
        return this.flagfall;
    }

    public void setFlagfall(BigDecimal flagfall) {
        this.flagfall = flagfall;
    }

    public BigDecimal getRate() {
        return this.rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public TimeUnitType getTimeUnit() {
        return this.timeUnit;
    }

    public void setTimeUnit(TimeUnitType timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isAutoEnd() {
        return this.autoEnd;
    }

    public void setAutoEnd(boolean autoEnd) {
        this.autoEnd = autoEnd;
    }

    public int getEarliestAutoEndSeconds() {
        return this.earliestAutoEndSeconds;
    }

    public void setEarliestAutoEndSeconds(int earliestAutoEndSeconds) {
        this.earliestAutoEndSeconds = earliestAutoEndSeconds;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<UserGroupEntity> getValidUserGroups() {
        return this.validUserGroups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProgramEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
