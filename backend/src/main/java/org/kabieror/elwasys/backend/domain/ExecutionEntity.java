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
 * Entspricht der Tabelle {@code executions} (eine Programm-Ausführung/ein Waschvorgang,
 * siehe docs/kb/02-data-model.md) sowie {@code org.kabieror.elwasys.common.Execution} im
 * Alt-Code. Die Lebenszyklus-Logik (Start/Ende/Reset, Preis-/Ablauf-Berechnung) liegt
 * bewusst NICHT auf der Entity, sondern in {@code ExecutionService} - siehe
 * docs/kb/05-migration-plan.md.
 */
@Entity
@Table(name = "executions")
public class ExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * EAGER: der Alt-Code lädt Gerät/Programm/Benutzer einer Ausführung immer sofort mit
     * (siehe {@code DataManager#getExecution(ResultSet)}) - siehe
     * {@code UserEntity#getGroup()} für die Begründung dieser Entscheidung.
     */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private ProgramEntity program;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column
    private LocalDateTime start;

    @Column
    private LocalDateTime stop;

    @Column(nullable = false)
    private boolean finished = false;

    protected ExecutionEntity() {
        // for JPA
    }

    public ExecutionEntity(DeviceEntity device, ProgramEntity program, UserEntity user) {
        this.device = device;
        this.program = program;
        this.user = user;
    }

    public Integer getId() {
        return this.id;
    }

    public DeviceEntity getDevice() {
        return this.device;
    }

    public ProgramEntity getProgram() {
        return this.program;
    }

    public UserEntity getUser() {
        return this.user;
    }

    public LocalDateTime getStart() {
        return this.start;
    }

    public void setStart(LocalDateTime start) {
        this.start = start;
    }

    public LocalDateTime getStop() {
        return this.stop;
    }

    public void setStop(LocalDateTime stop) {
        this.stop = stop;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecutionEntity that)) {
            return false;
        }
        return this.id != null && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
