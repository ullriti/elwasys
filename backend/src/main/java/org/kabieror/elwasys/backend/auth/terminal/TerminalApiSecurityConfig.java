package org.kabieror.elwasys.backend.auth.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Eigene, zustandslose {@link SecurityFilterChain} für die Terminal-REST-API/den
 * WebSocket-Handshake ({@code /api/v1/**}) - wie in {@code SecurityConfig} (AP3) als nächster
 * Schritt vorgesehen (siehe dessen Klassen-Javadoc), ohne diese Klasse zu ändern.
 *
 * <p>{@code @Order(1)}: Spring Security wertet mehrere {@link SecurityFilterChain}s in
 * {@code @Order}-Reihenfolge aus und nimmt die erste Kette, deren
 * {@code securityMatcher(...)} zutrifft. Diese Kette bekommt die niedrigste Order-Zahl (=
 * höchste Priorität) und greift daher für {@code /api/v1/**} zuerst; {@code SecurityConfig}s
 * Kette (keine explizite {@code @Order} = niedrigste Priorität) bleibt unverändert die
 * Catch-all-Kette für alles andere (Admin-Portal, Actuator).
 *
 * <p>Zustandslos ({@link SessionCreationPolicy#STATELESS}) und ohne CSRF-Schutz: Terminals
 * sind keine Browser-Clients mit Session-Cookies, jede Anfrage authentifiziert sich
 * eigenständig über ihr Standort-Token ({@link TerminalTokenAuthenticationFilter}).
 *
 * <p><b>Zwei Fallstricke, die beim Aufbau dieser Klasse auftraten (siehe
 * docs/kb/05-migration-plan.md, Änderungslog/AP4, für die volle Herleitung)</b>:
 * <ol>
 *   <li>{@link #disableAutoFilterRegistration}: jede Spring-Bean vom Typ
 *       {@code jakarta.servlet.Filter} wird von Spring Boot standardmäßig ZUSÄTZLICH als
 *       eigenständiger Servlet-Filter für ALLE Pfade registriert (nicht nur innerhalb dieser
 *       {@link SecurityFilterChain}) - das hätte {@code TerminalTokenAuthenticationFilter}
 *       auch vor {@code /actuator/health} gehängt. Die {@link FilterRegistrationBean} mit
 *       {@code setEnabled(false)} unterdrückt genau diese globale Auto-Registrierung; die
 *       Verwendung über {@code addFilterBefore(...)} unten bleibt davon unberührt.</li>
 *   <li>{@code securityMatcher(String...)} statt {@link AntPathRequestMatcher}: die
 *       String-Overload versucht, Spring MVC zu erkennen und dafür einen
 *       {@code MvcRequestMatcher} zu bauen, der die Bean {@code mvcHandlerMappingIntrospector}
 *       braucht - die existiert nur in einem vollen Web-MVC-Kontext, nicht in den
 *       {@code webEnvironment=NONE}-Tests dieses Moduls ({@code AbstractBackendIT}), obwohl
 *       {@code spring-boot-starter-web} auf dem Klassenpfad liegt. Der explizite
 *       {@link AntPathRequestMatcher} umgeht diese MVC-Erkennung vollständig.</li>
 * </ol>
 */
@Configuration
public class TerminalApiSecurityConfig {

    @Bean
    public TerminalTokenAuthenticationFilter terminalTokenAuthenticationFilter(TerminalTokenService terminalTokenService,
            ObjectMapper objectMapper) {
        return new TerminalTokenAuthenticationFilter(terminalTokenService, objectMapper);
    }

    /**
     * Siehe Klassen-Javadoc, Punkt 1: verhindert, dass Spring Boot
     * {@link TerminalTokenAuthenticationFilter} zusätzlich als globalen Servlet-Filter für
     * ALLE Pfade registriert. Die Filterwirkung bleibt ausschließlich auf die
     * {@code /api/v1/**}-Sicherheitskette unten beschränkt.
     */
    @Bean
    public FilterRegistrationBean<TerminalTokenAuthenticationFilter> terminalTokenAuthenticationFilterRegistration(
            TerminalTokenAuthenticationFilter terminalTokenAuthenticationFilter) {
        FilterRegistrationBean<TerminalTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(
                terminalTokenAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain terminalApiSecurityFilterChain(HttpSecurity http,
            TerminalTokenAuthenticationFilter terminalTokenAuthenticationFilter) throws Exception {
        http.securityMatcher(new AntPathRequestMatcher("/api/v1/**"))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAuthority("ROLE_TERMINAL"))
                // Terminal-Token-Filter ersetzt für diese Kette das Formular-/Session-Login der
                // AP3-Kette vollständig - registriert an derselben Stelle
                // (UsernamePasswordAuthenticationFilter), an der ein Login-Filter üblicherweise
                // sitzt, damit die Authentifizierung vor der Autorisierungsprüfung greift.
                .addFilterBefore(terminalTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        // Absichtlich kein zusätzlicher exceptionHandling()-Entry-Point: der Filter selbst
        // beantwortet ein fehlendes/ungültiges Token bereits mit 401 + ProblemDetail (siehe
        // TerminalTokenAuthenticationFilter) und lässt den Request in diesem Fall gar nicht
        // erst bis zur Autorisierungsprüfung durch.
        return http.build();
    }
}
