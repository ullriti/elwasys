package org.kabieror.elwasys.backend.auth.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistenz-/Kryptografieverhalten von {@link TerminalTokenService} (AP4, siehe
 * kb/05-migration-plan.md): Token-Erzeugung, -Prüfung, Rotation (mehrere aktive Tokens je
 * Standort) und Widerruf.
 */
class TerminalTokenServiceTest extends AbstractBackendIT {

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private TerminalTokenService terminalTokenService;

    private LocationEntity newLocation() {
        return this.locationRepository.save(new LocationEntity(Fixtures.unique("loc")));
    }

    @Test
    void createdTokenAuthenticatesToItsLocation() {
        LocationEntity location = newLocation();

        IssuedTerminalToken issued = this.terminalTokenService.createToken(location, "terminal-1");

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.entity().getId()).isNotNull();
        assertThat(issued.entity().isActive()).isTrue();

        var authenticated = this.terminalTokenService.authenticate(issued.rawToken());
        assertThat(authenticated).isPresent();
        assertThat(authenticated.get().getLocation().getId()).isEqualTo(location.getId());
    }

    @Test
    void rawTokenIsNeverStoredInPlainText() {
        LocationEntity location = newLocation();
        IssuedTerminalToken issued = this.terminalTokenService.createToken(location, null);

        assertThat(issued.entity().getTokenHash()).isNotEqualTo(issued.rawToken());
        assertThat(issued.entity().getTokenHash()).hasSize(64); // SHA-256 hex
    }

    @Test
    void unknownTokenDoesNotAuthenticate() {
        assertThat(this.terminalTokenService.authenticate("elwt_does-not-exist")).isEmpty();
    }

    @Test
    void blankOrNullTokenDoesNotAuthenticate() {
        assertThat(this.terminalTokenService.authenticate("")).isEmpty();
        assertThat(this.terminalTokenService.authenticate(null)).isEmpty();
    }

    @Test
    void revokedTokenNoLongerAuthenticates() {
        LocationEntity location = newLocation();
        IssuedTerminalToken issued = this.terminalTokenService.createToken(location, null);

        boolean revoked = this.terminalTokenService.revoke(issued.entity().getId());

        assertThat(revoked).isTrue();
        assertThat(this.terminalTokenService.authenticate(issued.rawToken())).isEmpty();
    }

    @Test
    void revokingAnUnknownTokenIdReturnsFalse() {
        assertThat(this.terminalTokenService.revoke(-987654)).isFalse();
    }

    @Test
    void locationCanHaveMultipleActiveTokensForRotationWithoutDowntime() {
        LocationEntity location = newLocation();
        IssuedTerminalToken oldToken = this.terminalTokenService.createToken(location, "old");
        IssuedTerminalToken newToken = this.terminalTokenService.createToken(location, "new");

        // Beide sind gleichzeitig gültig - das Terminal kann auf das neue Token umgestellt
        // werden, bevor das alte widerrufen wird (Rotation ohne Ausfallfenster).
        assertThat(this.terminalTokenService.authenticate(oldToken.rawToken())).isPresent();
        assertThat(this.terminalTokenService.authenticate(newToken.rawToken())).isPresent();

        this.terminalTokenService.revoke(oldToken.entity().getId());

        assertThat(this.terminalTokenService.authenticate(oldToken.rawToken())).as("revoked old token").isEmpty();
        assertThat(this.terminalTokenService.authenticate(newToken.rawToken())).as("new token unaffected")
                .isPresent();
    }

    @Test
    void authenticateUpdatesLastUsedAt() {
        LocationEntity location = newLocation();
        IssuedTerminalToken issued = this.terminalTokenService.createToken(location, null);
        assertThat(issued.entity().getLastUsedAt()).isNull();

        TerminalTokenEntity afterUse = this.terminalTokenService.authenticate(issued.rawToken()).orElseThrow();

        assertThat(afterUse.getLastUsedAt()).isNotNull();
    }
}
