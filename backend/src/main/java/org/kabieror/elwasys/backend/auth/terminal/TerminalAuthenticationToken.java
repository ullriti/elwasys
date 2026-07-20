package org.kabieror.elwasys.backend.auth.terminal;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * {@link org.springframework.security.core.Authentication} für einen per Standort-Token
 * authentifizierten Terminal-API-Aufruf. Trägt bewusst kein Credential (analog
 * {@code ElwasysUserPrincipal#getPassword()}): das Klartext-Token wird nach der Prüfung
 * durch {@link TerminalTokenAuthenticationFilter} nicht weiter gehalten.
 */
public class TerminalAuthenticationToken extends AbstractAuthenticationToken {

    private final TerminalPrincipal principal;

    public TerminalAuthenticationToken(TerminalPrincipal principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public TerminalPrincipal getPrincipal() {
        return this.principal;
    }
}
