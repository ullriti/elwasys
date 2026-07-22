package org.kabieror.elwasys.backend.auth.terminal;

/**
 * Authentifizierter Standort-Kontext eines Terminal-API-Aufrufs (siehe
 * {@link TerminalTokenAuthenticationFilter}). Wird als
 * {@link org.springframework.security.core.Authentication#getPrincipal()} in den
 * {@code SecurityContext} gelegt - REST-Controller/der WebSocket-Handshake lesen ihn per
 * {@code @AuthenticationPrincipal TerminalPrincipal} bzw. aus dem
 * {@code SecurityContextHolder}, um den Standort-Scope durchzusetzen (docs/kb/05-migration-plan.md,
 * AP4: "ein Token sieht nur Geräte/Executions seines Standorts").
 */
public record TerminalPrincipal(Integer tokenId, Integer locationId, String locationName) {
}
