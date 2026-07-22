package org.kabieror.elwasys.backend.ui;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.shared.ui.Transport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * Aktiviert Vaadin Push für das gesamte Admin-Portal (Phase 3 AP5, siehe
 * docs/kb/05-migration-plan.md, Roadmap-Punkt "Live-Updates zwischen Sessions") - ersatzlos ergänzt
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
 * docs/kb/05-migration-plan.md, AP5-Verifikation): Vaadins Push-Endpunkt liegt unter dem
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

    /**
     * Portal-Design (AdminLTE-Look des Alt-Portals) - bewusst NICHT als kompiliertes
     * Vaadin-Theme ({@code @Theme}/{@code src/main/frontend/themes/...}), sondern zur Laufzeit
     * als dokumentweites Inline-Stylesheet in den {@code <head>} eingehängt.
     *
     * <p><b>Warum kein {@code @Theme}:</b> Ein eigenes Theme (oder irgendein
     * {@code @CssImport}/{@code @JsModule}) zwingt Vaadin, ein anwendungs-spezifisches
     * Frontend-Bundle zu kompilieren ({@code vaadin:build-frontend}). Genau dieser Schritt löst
     * bei der hier verwendeten Vaadin-Version 24.10.x (als "extended maintenance"/kommerziell
     * eingestuft) einen ONLINE-Lizenzcheck gegen vaadin.com aus (siehe Begründung in der
     * pom.xml sowie docs/kb/05-migration-plan.md "Phase 3 AP2"). In der Sandbox/CI ist vaadin.com
     * nicht erreichbar (Proxy liefert 403), der Produktionsbuild bricht dann ab. Ohne eigenes
     * Frontend nutzt die Anwendung das mitgelieferte Standard-Bundle, der Lizenzcheck entfällt
     * (deshalb baut die E2E-Pipeline bisher grün). Das Inline-Stylesheet umgeht das komplett:
     * es wird serverseitig zur Laufzeit ausgeliefert und berührt das Frontend-Bundle nicht.
     *
     * <p><b>Warum das für das Styling reicht:</b> Das CSS arbeitet ausschließlich mit Mitteln,
     * die aus dem Dokument-Scope bis in die Web-Components wirken - Lumo-Custom-Properties
     * (vererben sich in die Shadow-DOMs) und {@code ::part()}-Selektoren auf den von Vaadin
     * exponierten Teilen (Navbar/Drawer der AppLayout, Grid-Zellen, SideNav-Items) - plus die
     * ohnehin im Light-DOM liegenden Klassen-Hooks der Views. Es verändert nur Farben/Rahmen,
     * keine Texte oder Struktur, damit die E2E-Suite (backend/e2e, P1-P20) unverändert bleibt.
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addInlineWithContents(Inline.Position.APPEND, loadPortalCss(), Inline.Wrapping.STYLESHEET);
    }

    private static String loadPortalCss() {
        try {
            return StreamUtils.copyToString(new ClassPathResource("portal-theme.css").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Portal-Theme-CSS (portal-theme.css) konnte nicht geladen werden", e);
        }
    }
}
