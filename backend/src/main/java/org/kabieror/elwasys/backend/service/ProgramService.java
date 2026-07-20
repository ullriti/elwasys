package org.kabieror.elwasys.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.EntityInUseException;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stammdaten-Verwaltung für Programme (Phase 3 AP2, siehe kb/05-migration-plan.md) -
 * fachlicher Nachfolger von {@code Portal/.../components/ProgramWindow} (Testfall P12).
 */
@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final DeviceRepository deviceRepository;

    public ProgramService(ProgramRepository programRepository, DeviceRepository deviceRepository) {
        this.programRepository = programRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public List<ProgramEntity> findAll() {
        return this.programRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<ProgramEntity> findById(Integer id) {
        return this.programRepository.findById(id);
    }

    @Transactional
    public ProgramEntity create(String name, ProgramType type, BigDecimal flagfall, BigDecimal rate,
            TimeUnitType timeUnit, Duration maxDuration, Duration freeDuration, boolean autoEnd,
            Duration earliestAutoEnd, boolean enabled, Set<UserGroupEntity> validUserGroups) {
        ProgramEntity program = new ProgramEntity(name, type, (int) maxDuration.getSeconds());
        applyFields(program, flagfall, rate, timeUnit, freeDuration, autoEnd, earliestAutoEnd, enabled,
                validUserGroups);
        return this.programRepository.save(program);
    }

    @Transactional
    public ProgramEntity update(ProgramEntity program, String name, ProgramType type, BigDecimal flagfall,
            BigDecimal rate, TimeUnitType timeUnit, Duration maxDuration, Duration freeDuration, boolean autoEnd,
            Duration earliestAutoEnd, boolean enabled, Set<UserGroupEntity> validUserGroups) {
        program.setName(name);
        program.setType(type);
        program.setMaxDurationSeconds((int) maxDuration.getSeconds());
        applyFields(program, flagfall, rate, timeUnit, freeDuration, autoEnd, earliestAutoEnd, enabled,
                validUserGroups);
        return this.programRepository.save(program);
    }

    private void applyFields(ProgramEntity program, BigDecimal flagfall, BigDecimal rate, TimeUnitType timeUnit,
            Duration freeDuration, boolean autoEnd, Duration earliestAutoEnd, boolean enabled,
            Set<UserGroupEntity> validUserGroups) {
        program.setFlagfall(flagfall);
        program.setRate(rate);
        program.setTimeUnit(timeUnit);
        program.setFreeDurationSeconds((int) freeDuration.getSeconds());
        program.setAutoEnd(autoEnd);
        program.setEarliestAutoEndSeconds((int) earliestAutoEnd.getSeconds());
        program.setEnabled(enabled);
        program.getValidUserGroups().clear();
        program.getValidUserGroups().addAll(validUserGroups);
    }

    /**
     * Löscht ein Programm. 1:1-Portierung des Lösch-Wächters aus
     * {@code Portal/.../views/ProgramsView#deleteProgram}: ein Programm, das noch mindestens
     * einem Gerät zugeordnet ist, wird nicht gelöscht (obwohl die DB selbst das über
     * {@code ON DELETE CASCADE} auf {@code device_program_rel} zulassen würde, siehe
     * kb/02-data-model.md) - das ist eine fachliche Schutzregel, keine technische
     * Notwendigkeit.
     *
     * @throws EntityInUseException wenn das Programm noch mindestens einem Gerät zugeordnet
     *                              ist; die Meldung enthält die Anzahl der betroffenen
     *                              Geräte (wie im Alt-Portal)
     */
    @Transactional
    public void delete(ProgramEntity program) {
        List<DeviceEntity> devicesUsingProgram = this.deviceRepository.findByPrograms_Id(program.getId());
        if (!devicesUsingProgram.isEmpty()) {
            throw new EntityInUseException(
                    "Das Programm " + program.getName() + " ist noch auf " + devicesUsingProgram.size()
                            + " Gerät(en) verfügbar.");
        }
        this.programRepository.delete(program);
    }
}
