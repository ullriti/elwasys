package org.kabieror.elwasys.backend.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;

/**
 * Aktiviert Vaadin Push für das gesamte Admin-Portal (Phase 3 AP5, siehe
 * kb/05-migration-plan.md, Roadmap-Punkt "Live-Updates zwischen Sessions") - ersatzlos ergänzt
 * das Alt-Portal NICHT (siehe {@link org.kabieror.elwasys.backend.ui.push.UiBroadcaster}
 * Javadoc: dort trotz {@code vaadin-push}-Abhängigkeit nirgends tatsächlich per {@code @Push}
 * aktiviert).
 *
 * <p>{@code transport = Transport.WEBSOCKET_XHR}: WebSocket als bevorzugter Transport mit
 * automatischem Fallback auf lang laufende XHR-Requests (Vaadins Standard-Kombination), falls
 * ein Proxy/eine Firewall zwischen Browser und Server WebSockets blockiert - robuster als ein
 * reines {@code WEBSOCKET}, ohne dass der Bedienfluss sich unterscheidet.
 *
 * <p><b>Security-/Pfad-Koexistenz mit dem Terminal-WebSocket-Endpunkt</b> (siehe
 * kb/05-migration-plan.md, AP5-Verifikation): Vaadins Push-Endpunkt liegt unter dem
 * servlet-internen Pfad {@code /VAADIN/push} (bzw. dem Atmosphere-Pfad relativ zur
 * Vaadin-Servlet-Zuordnung), der Terminal-WebSocket-Endpunkt dagegen unter
 * {@code /api/v1/terminal-ws} (siehe {@code TerminalWebSocketConfig}) - disjunkte Pfade, keine
 * Kollision. Sicherheitsseitig deckt {@code VaadinSecurityConfigurer} (siehe
 * {@code auth.SecurityConfig}) den Push-Endpunkt automatisch mit ab: er zählt zu den
 * "Vaadin-internen" Anfragen, die {@code VaadinSecurityConfigurer} unabhängig vom
 * Anmeldestatus durchlässt (dieselbe Freigabe wie für Themes/Icons/das JS-Bundle selbst) -
 * ohne das müsste ein Push-Handshake vor dem Login fehlschlagen; die eigentliche Autorisierung
 * (welche Daten eine Session sehen darf) bleibt unverändert Sache der einzelnen Views
 * ({@code @RolesAllowed} usw.), der Push-Kanal selbst transportiert nur bereits
 * serverseitig autorisierte UI-Updates. Die Terminal-API-Kette
 * ({@code TerminalApiSecurityConfig}, {@code @Order(1)}, {@code securityMatcher("/api/v1/**")})
 * ist davon unberührt, weil sie nur für ihren eigenen Pfad-Präfix zuständig ist.
 */
@com.vaadin.flow.component.page.Push(value = PushMode.AUTOMATIC, transport = Transport.WEBSOCKET_XHR)
public class ElwasysAppShell implements AppShellConfigurator {
}
