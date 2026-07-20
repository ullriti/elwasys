package org.kabieror.elwasys.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.auth.terminal.IssuedTerminalToken;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.support.AbstractApiIT;

/**
 * End-to-End-Beweis der zustandslosen Terminal-Sicherheitskette
 * ({@code TerminalApiSecurityConfig}, AP4, siehe kb/05-migration-plan.md): fehlendes,
 * unbekanntes, widerrufenes und gültiges Standort-Token, jeweils über die echte
 * HTTP-/Servlet-Schicht (analog {@code SecurityConfigTest} aus AP3).
 */
class TerminalApiSecurityTest extends AbstractApiIT {

    @Test
    void missingTokenIsRejectedWith401() throws Exception {
        this.mockMvc.perform(get("/api/v1/locations/me")).andExpect(status().isUnauthorized()).andExpect(
                jsonPath("$.type").value("urn:elwasys:terminal-token-invalid"));
    }

    @Test
    void malformedAuthorizationHeaderIsRejectedWith401() throws Exception {
        this.mockMvc.perform(get("/api/v1/locations/me").header("Authorization", "Basic abc123")).andExpect(
                status().isUnauthorized());
    }

    @Test
    void unknownTokenIsRejectedWith401() throws Exception {
        this.mockMvc.perform(get("/api/v1/locations/me").header("Authorization", "Bearer elwt_no-such-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenIsRejectedWith401() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);
        this.terminalTokenService.revoke(token.entity().getId());

        this.mockMvc.perform(get("/api/v1/locations/me").header("Authorization", authHeader(token))).andExpect(
                status().isUnauthorized());
    }

    @Test
    void validTokenIsAcceptedAndResolvesToItsLocation() throws Exception {
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);

        this.mockMvc.perform(get("/api/v1/locations/me").header("Authorization", authHeader(token))).andExpect(
                status().isOk()).andExpect(jsonPath("$.id").value(location.getId())).andExpect(
                jsonPath("$.name").value(location.getName()));
    }

    @Test
    void terminalTokenDoesNotGrantAccessToTheAdminPortalCatchAllChain() throws Exception {
        // Ein Terminal-Token authentifiziert nur gegen die /api/v1/**-Kette
        // (TerminalApiSecurityConfig) - für alles andere bleibt SecurityConfig (AP3, Session-
        // Login) zuständig. Ein gültiges Terminal-Token darf keinen Zugriff auf die
        // Catch-all-Kette gewähren.
        LocationEntity location = newLocation();
        IssuedTerminalToken token = newToken(location);

        this.mockMvc.perform(get("/actuator/env").header("Authorization", authHeader(token))).andExpect(
                status().is3xxRedirection());
    }
}
