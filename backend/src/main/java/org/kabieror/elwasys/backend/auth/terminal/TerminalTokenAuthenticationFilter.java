package org.kabieror.elwasys.backend.auth.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.TerminalTokenEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authentifiziert Anfragen an {@code /api/v1/**} über ein Standort-Token (Phase 2 AP4, siehe
 * docs/kb/05-migration-plan.md, Technologie-Entscheidung "API-Auth"). Ist ausschließlich in
 * {@link TerminalApiSecurityConfig}s zustandsloser Sicherheitskette registriert - für alle
 * anderen Pfade (Admin-Portal, Actuator) bleibt {@code SecurityConfig} (AP3) unverändert
 * zuständig.
 *
 * <p><b>Entscheidung - Header</b>: das Token wird als Standard-{@code Authorization}-Header
 * im Bearer-Schema erwartet ({@code Authorization: Bearer <token>}), nicht als proprietärer
 * Header. Begründung: das ist der von HTTP-Clients/-Bibliotheken (inkl.
 * {@code java.net.http}, das der Client laut docs/kb/05-migration-plan.md ohnehin für die
 * Backend-Anbindung nutzen soll) nativ unterstützte Mechanismus, funktioniert unverändert für
 * den WebSocket-Handshake (ein Java-{@code WebSocket}-Client kann beliebige Header beim
 * Handshake setzen - anders als ein Browser-{@code WebSocket}, der das nicht kann; das ist
 * hier unerheblich, da ausschließlich das Java-Terminal verbindet, kein Browser), und
 * vermeidet einen weiteren, elwasys-spezifischen Header ohne fachlichen Mehrwert.
 *
 * <p>Bei fehlendem/unbekanntem/widerrufenem Token wird die Anfrage HIER bereits mit
 * {@code 401} + {@link ProblemDetail} beantwortet (statt die Sicherheitskette über eine
 * {@code AuthenticationException} entscheiden zu lassen) - das hält den Fehlerfall für den
 * einzigen Authentifizierungsweg dieser Kette einfach und einheitlich.
 */
public class TerminalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalTokenAuthenticationFilter.class);

    private final TerminalTokenService terminalTokenService;

    private final ObjectMapper objectMapper;

    public TerminalTokenAuthenticationFilter(TerminalTokenService terminalTokenService, ObjectMapper objectMapper) {
        this.terminalTokenService = terminalTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawToken = extractBearerToken(request);
        if (rawToken == null) {
            respondUnauthorized(response, "Es wurde kein Standort-Token angegeben (Authorization: Bearer <token>).");
            return;
        }

        Optional<TerminalTokenEntity> tokenEntity = this.terminalTokenService.authenticate(rawToken);
        if (tokenEntity.isEmpty()) {
            respondUnauthorized(response, "Das angegebene Standort-Token ist unbekannt oder wurde widerrufen.");
            return;
        }

        TerminalTokenEntity entity = tokenEntity.get();
        TerminalPrincipal principal = new TerminalPrincipal(entity.getId(), entity.getLocation().getId(),
                entity.getLocation().getName());
        TerminalAuthenticationToken authentication = new TerminalAuthenticationToken(principal,
                List.of(new SimpleGrantedAuthority("ROLE_TERMINAL")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        LOG.debug("Authenticated terminal request for location '{}' (id={}, tokenId={}).", principal.locationName(),
                principal.locationId(), principal.tokenId());

        chain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private void respondUnauthorized(HttpServletResponse response, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setType(URI.create("urn:elwasys:terminal-token-invalid"));
        problem.setTitle("Ungültiges Standort-Token");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(this.objectMapper.writeValueAsString(problem));
    }
}
