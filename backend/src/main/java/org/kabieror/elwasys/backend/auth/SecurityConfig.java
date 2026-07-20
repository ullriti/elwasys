package org.kabieror.elwasys.backend.auth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Session-basiertes Login-Fundament (AP3, siehe kb/05-migration-plan.md) für das künftige
 * Vaadin-Flow-Admin-Portal (Phase 3). {@link ElwasysAuthenticationProvider} ist die einzige
 * Authentifizierungsquelle.
 *
 * <p>Dieses Arbeitspaket führt noch KEINE fachlichen HTTP-Endpunkte ein - die REST-API für
 * Terminals (Standort-Token-Auth statt Session-Login, siehe kb/05-migration-plan.md
 * Zielarchitektur/Technologie-Entscheidungen "API-Auth") folgt in AP4. Diese Konfiguration
 * ist bewusst so gehalten, dass AP4 andocken kann, ohne diese Klasse zu ändern:
 * <ul>
 *   <li>Terminals brauchen eine EIGENE, zustandslose {@link SecurityFilterChain}
 *       (Token-Filter statt Formular-Login/Session) - AP4 sollte dafür eine zweite
 *       {@code @Bean}-Methode mit einem {@code securityMatcher(...)} (z.B. auf
 *       {@code /api/v1/**}) und einer NIEDRIGEREN {@code @Order}-Zahl (höhere Priorität)
 *       als diese Kette ergänzen; Spring Security wertet mehrere Ketten in
 *       {@code @Order}-Reihenfolge aus und nimmt die erste zutreffende
 *       ({@code securityMatcher}).</li>
 *   <li>Diese Kette bleibt die "Catch-all"-Kette für Admin-Portal/Actuator und braucht
 *       daher keine explizite {@code @Order} (Default = niedrigste Priorität, greift erst
 *       wenn keine speziellere Kette zuständig ist).</li>
 * </ul>
 *
 * <p>Aktuell abgesichert: {@code /actuator/health} ist öffentlich erreichbar (Auftrag AP3),
 * alles andere verlangt eine Anmeldung (Default-Deny).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(ElwasysAuthenticationProvider elwasysAuthenticationProvider) {
        return new ProviderManager(List.<AuthenticationProvider>of(elwasysAuthenticationProvider));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                                .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
