package org.kabieror.elwasys.backend.support;

import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.auth.terminal.TerminalTokenService;
import org.kabieror.elwasys.backend.domain.DeviceEntity;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.repository.DeviceRepository;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.repository.ProgramRepository;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.service.CreditService;
import org.kabieror.elwasys.backend.service.ExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Basisklasse für Integrationstests der Terminal-REST-API ({@code /api/v1/**}, AP4, siehe
 * docs/kb/05-migration-plan.md). Eigene {@code @SpringBootTest}-Konfiguration statt
 * {@link org.kabieror.elwasys.backend.support.AbstractBackendIT}: diese Tests brauchen einen
 * echten (Mock-)Servlet-Container, um BEIDE Sicherheitsketten (AP3-Catch-all +
 * {@code TerminalApiSecurityConfig}) end-to-end zu durchlaufen (analog
 * {@code SecurityConfigTest} aus AP3).
 *
 * <p>Stellt gemeinsame Fixture-Helfer bereit (Standort/Token/Benutzer/Gerät/Programm anlegen)
 * - siehe {@link Fixtures} für eindeutige Namen (Testdaten werden committet, nicht
 * zurückgerollt).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractApiIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected LocationRepository locationRepository;

    @Autowired
    protected TerminalTokenService terminalTokenService;

    @Autowired
    protected UserGroupRepository userGroupRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected DeviceRepository deviceRepository;

    @Autowired
    protected ProgramRepository programRepository;

    @Autowired
    protected CreditService creditService;

    @Autowired
    protected ExecutionService executionService;

    protected LocationEntity newLocation() {
        return this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
    }

    protected IssuedTerminalToken newToken(LocationEntity location) {
        return this.terminalTokenService.createToken(location, Fixtures.unique("label"));
    }

    protected UserGroupEntity newGroup() {
        return this.userGroupRepository.save(new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
    }

    protected UserEntity newUser(UserGroupEntity group) {
        return this.userRepository.save(new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group));
    }

    protected UserEntity newUserWithCard(UserGroupEntity group, String cardId) {
        UserEntity user = new UserEntity(Fixtures.unique("Name"), Fixtures.unique("user"), group);
        user.setCardIds(new String[] {cardId});
        return this.userRepository.save(user);
    }

    protected DeviceEntity newDevice(LocationEntity location) {
        return this.deviceRepository.save(new DeviceEntity(Fixtures.unique("dev"), 1, location));
    }

    protected ProgramEntity newFixedProgram(java.math.BigDecimal flagfall) {
        ProgramEntity program = new ProgramEntity(Fixtures.unique("prog"), ProgramType.FIXED, 3600);
        program.setFlagfall(flagfall);
        program.setFreeDurationSeconds(0);
        return this.programRepository.save(program);
    }

    /**
     * Ordnet ein Programm einem Gerät zu und gibt beide für Berechtigungsprüfungen frei
     * (Gruppe von {@code user} an Standort/Gerät/Programm zulassen) - Kurzform für die
     * Standard-Konstellation "ein Benutzer darf an einem Standort ein Gerät mit einem
     * Programm benutzen", wie sie die meisten API-Tests brauchen.
     */
    protected void allow(LocationEntity location, DeviceEntity device, ProgramEntity program, UserGroupEntity group) {
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);
        device.getValidUserGroups().add(group);
        device.getPrograms().add(program);
        this.deviceRepository.save(device);
        program.getValidUserGroups().add(group);
        this.programRepository.save(program);
    }

    protected String authHeader(IssuedTerminalToken token) {
        return "Bearer " + token.rawToken();
    }
}
