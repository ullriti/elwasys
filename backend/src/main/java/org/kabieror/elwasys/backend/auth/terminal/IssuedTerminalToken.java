package org.kabieror.elwasys.backend.auth.terminal;

import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;

/**
 * Ergebnis von {@link TerminalTokenService#createToken}: das Klartext-Token
 * ({@link #rawToken()}) existiert nur zur Laufzeit dieses Aufrufs - es wird NICHT
 * gespeichert (siehe {@link TerminalTokenEntity#getTokenHash()}) und ist danach nicht mehr
 * rekonstruierbar. Aufrufer müssen es sofort und einmalig an den Bediener ausgeben (siehe
 * {@code TerminalTokenCliRunner}).
 */
public record IssuedTerminalToken(String rawToken, TerminalTokenEntity entity) {
}
