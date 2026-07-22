package org.kabieror.elwasys.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.support.AbstractApiIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.http.MediaType;

/**
 * Kartenlogin über die Terminal-API (AP4, siehe docs/kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code MainFormController#onCardDetected}: unbekannte Kartennummer,
 * gesperrter Benutzer, standortfremde Benutzergruppe, erfolgreicher Login inkl. Guthaben.
 */
class CardLoginControllerTest extends AbstractApiIT {

    @Test
    void unknownCardIdReturns404() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);

        // Formal gültige (hex) aber nicht vergebene Kartennummer -> 404 card-not-found.
        this.mockMvc.perform(post("/api/v1/card-login").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":\"deadbeef\"}")).andExpect(
                status().isNotFound()).andExpect(jsonPath("$.type").value("urn:elwasys:card-not-found"));
    }

    /**
     * Regressionstest für die Regex-Injection (Issue #21, Pre-Launch AP4): eine als
     * Kartennummer übergebene Regex ({@code .*}) hätte mit der alten Query jeden beliebigen
     * Benutzer angemeldet. Sie wird jetzt bereits von der strengen Formatvalidierung
     * ({@code CardLoginRequest}, {@code @Pattern}) an der API-Grenze mit {@code 400} abgewiesen
     * - der Angriff erreicht die Persistenz gar nicht erst (die regex-freie Query ist die
     * zweite Verteidigungslinie, siehe {@code UserRepositoryCardIdTest}).
     */
    @Test
    void regexMetacharacterCardIdIsRejectedWith400() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        UserGroupEntity group = newGroup();
        // Ein echter Benutzer mit einer echten Karte ist vorhanden - trotzdem darf ".*" ihn
        // NICHT anmelden.
        newUserWithCard(group, Fixtures.uniqueCardId());
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);

        this.mockMvc.perform(post("/api/v1/card-login").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":\".*\"}")).andExpect(
                status().isBadRequest());
    }

    @Test
    void blockedUserIsRejectedWith403() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        UserGroupEntity group = newGroup();
        String cardId = Fixtures.uniqueCardId();
        UserEntity user = newUserWithCard(group, cardId);
        user.setBlocked(true);
        this.userRepository.save(user);
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);

        this.mockMvc.perform(post("/api/v1/card-login").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":\"" + cardId + "\"}")).andExpect(
                status().isForbidden()).andExpect(jsonPath("$.type").value("urn:elwasys:user-blocked"));
    }

    @Test
    void userWhoseGroupIsNotAllowedAtThisLocationIsRejectedWith403() throws Exception {
        LocationEntity location = newLocation(); // keine erlaubte Gruppe hinzugefügt
        IssuedTerminalToken token = newToken(location);
        UserGroupEntity group = newGroup();
        String cardId = Fixtures.uniqueCardId();
        newUserWithCard(group, cardId);

        this.mockMvc.perform(post("/api/v1/card-login").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":\"" + cardId + "\"}")).andExpect(
                status().isForbidden()).andExpect(jsonPath("$.type").value("urn:elwasys:location-not-allowed"));
    }

    @Test
    void successfulCardLoginReturnsUserDataIncludingCredit() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        location.getValidUserGroups().add(group);
        this.locationRepository.save(location);
        String cardId = Fixtures.uniqueCardId();
        UserEntity user = newUserWithCard(group, cardId);
        this.creditService.inpayment(user, new java.math.BigDecimal("12.50"));

        this.mockMvc.perform(post("/api/v1/card-login").header("Authorization", authHeader(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":\"" + cardId + "\"}")).andExpect(
                status().isOk()).andExpect(jsonPath("$.id").value(user.getId())).andExpect(
                jsonPath("$.username").value(user.getUsername())).andExpect(jsonPath("$.credit").value(12.5))
                .andExpect(jsonPath("$.blocked").value(false));
    }
}
