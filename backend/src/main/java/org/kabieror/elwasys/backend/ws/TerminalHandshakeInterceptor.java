package org.kabieror.elwasys.backend.ws;

import java.util.Map;
import org.kabieror.elwasys.backend.auth.terminal.TerminalAuthenticationToken;
import org.kabieror.elwasys.backend.auth.terminal.TerminalPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Übernimmt den bereits vor dem WebSocket-Upgrade authentifizierten Standort-Kontext in die
 * {@link org.springframework.web.socket.WebSocketSession}-Attribute (AP4, siehe
 * kb/05-migration-plan.md: "authentifiziert mit demselben Standort-Token beim Handshake").
 *
 * <p>Der WebSocket-Handshake ist zunächst eine normale HTTP-Anfrage - sie durchläuft daher
 * dieselbe {@code securityMatcher("/api/v1/**")}-Sicherheitskette wie die REST-Endpunkte
 * (siehe {@code TerminalApiSecurityConfig}), inklusive des
 * {@code TerminalTokenAuthenticationFilter}. Bis dieser Interceptor läuft, ist die
 * Authentifizierung also bereits erfolgt oder die Anfrage wurde vorher mit {@code 401}
 * beendet - {@link #beforeHandshake} liest das Ergebnis nur noch aus dem
 * {@code SecurityContext} desselben Request-Threads.
 */
public class TerminalHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(TerminalHandshakeInterceptor.class);

    public static final String ATTR_LOCATION_ID = "elwasys.terminal.locationId";

    public static final String ATTR_LOCATION_NAME = "elwasys.terminal.locationName";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof TerminalAuthenticationToken) || !(authentication.getPrincipal()
                instanceof TerminalPrincipal principal)) {
            // Sollte durch die Sicherheitskette bereits ausgeschlossen sein (defensiv).
            LOG.warn("Rejecting WebSocket handshake without a valid terminal token authentication.");
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_LOCATION_ID, principal.locationId());
        attributes.put(ATTR_LOCATION_NAME, principal.locationName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
            Exception exception) {
        // Kein Nachbereitungsbedarf.
    }
}
