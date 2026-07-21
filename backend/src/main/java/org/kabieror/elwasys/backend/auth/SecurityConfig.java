package org.kabieror.elwasys.backend.auth;

import java.util.List;
import org.kabieror.elwasys.backend.ui.login.LoginView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizedUrl;
import org.springframework.security.web.SecurityFilterChain;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

/**
 * Session-basiertes Login-Fundament (AP3, seit Phase 3 AP1 an das Vaadin-Flow-Admin-Portal
 * angebunden, siehe kb/05-migration-plan.md). {@link ElwasysAuthenticationProvider} ist
 * weiterhin die einzige Authentifizierungsquelle.
 *
 * <p>Diese Kette bleibt die "Catch-all"-Kette für Admin-Portal/Actuator/API-Dokumentation
 * (keine explizite {@code @Order} = niedrigste Priorität, greift nur wenn keine speziellere
 * Kette - siehe {@link org.kabieror.elwasys.backend.auth.terminal.TerminalApiSecurityConfig}
 * für {@code /api/v1/**} - zuständig ist).
 *
 * <p><b>Phase 3 AP1 (Vaadin-Integration, siehe kb/05-migration-plan.md)</b>: statt eines
 * generischen {@code formLogin(Customizer.withDefaults())} registriert
 * {@link VaadinSecurityConfigurer#vaadin()} jetzt {@link LoginView} als Login-Ziel
 * ({@code loginView(LoginView.class)}) - das übernimmt u.a. das Freischalten der
 * Login-Route + aller Vaadin-internen statischen Ressourcen (Themes, Icons, Push-Endpunkt)
 * für nicht angemeldete Anfragen sowie die rollenbasierte {@code NavigationAccessControl}
 * (routenweise Zugriffsprüfung über {@code @RolesAllowed}/{@code @PermitAll} an den einzelnen
 * Views, siehe {@code ui}-Paket) - der {@link AuthenticationManager} bleibt dabei unverändert
 * dieser Konfiguration zugeordnet ({@code ElwasysAuthenticationProvider} prüft also weiterhin
 * jeden Login). Die eigene {@code anyRequest(...)}-Regel unten deckt alle NICHT von Vaadin
 * selbst verwalteten Pfade ab (Actuator-Dokumentation, OpenAPI/Swagger-UI); die eigentliche
 * "alles andere erfordert eine Anmeldung"-Regel wird bewusst über
 * {@link VaadinSecurityConfigurer#anyRequest(java.util.function.Consumer)} gesetzt (statt über
 * eine eigene {@code .anyRequest()}-Regel), weil Spring Security pro Sicherheitskette nur EINE
 * catch-all-{@code anyRequest()}-Regel zulässt und Vaadins Konfigurator seine eigenen,
 * spezifischeren Freigaben (Login-Route, statische Ressourcen) vor dieser catch-all-Regel
 * einträgt.
 *
 * <p>Aktuell abgesichert: {@code /actuator/health} ist öffentlich erreichbar (Auftrag AP3),
 * alles andere verlangt eine Anmeldung (Default-Deny).
 *
 * <p>{@code @ConditionalOnWebApplication} auf {@link #securityFilterChain} (seit Phase 3 AP1
 * nötig geworden, siehe kb/05-migration-plan.md): mehrere bestehende Backend-Tests (AP2,
 * siehe {@code AbstractBackendIT}) laufen bewusst mit {@code webEnvironment=NONE} (kein Web-/
 * Servlet-Kontext, siehe deren Javadoc) und laden dabei trotzdem den kompletten
 * {@code ApplicationContext} inkl. dieser Klasse, weil {@code @SpringBootTest} ohne
 * Slice-Annotation immer alle {@code @Configuration}-Beans einliest. Vor AP1 war das
 * unproblematisch (Spring Securitys generisches {@code formLogin(...)} baut die
 * {@link SecurityFilterChain} rein deklarativ, ohne selbst einen {@code ServletContext} zu
 * benötigen). {@link VaadinSecurityConfigurer} braucht für den Login-Routen-Pfad dagegen
 * zwingend einen echten {@code ServletContext}-Bean ({@code getServletContextPath()}) - ohne
 * diese Bedingung würde die Bean-Erzeugung in einem Nicht-Web-Kontext mit einer
 * {@code NullPointerException} fehlschlagen und ALLE {@code webEnvironment=NONE}-Tests
 * reißen (nicht nur neue Vaadin-Tests). Unschädlich für alle NONE-Tests: keiner von ihnen
 * verwendet {@link SecurityFilterChain} direkt - sie testen {@link ElwasysAuthenticationProvider}
 * und andere {@code @Component}s eigenständig (siehe z.B. {@code ElwasysAuthenticationProviderTest}).
 * Bewusst NUR auf dieser Bean-Methode, NICHT auf der ganzen Klasse: {@code @EnableWebSecurity}
 * muss unconditional aktiv bleiben, weil es den (Prototype-)Bean {@link HttpSecurity} bereitstellt,
 * den auch {@link org.kabieror.elwasys.backend.auth.terminal.TerminalApiSecurityConfig} braucht
 * (dessen eigene Kette hat KEINE Vaadin-/ServletContext-Abhängigkeit und funktioniert daher in
 * {@code webEnvironment=NONE} unverändert, wie schon vor AP1) - ebenso bleibt der
 * {@link #authenticationManager} Bean unconditional (baut nur einen {@link ProviderManager},
 * keine Servlet-Abhängigkeit).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public AuthenticationManager authenticationManager(ElwasysAuthenticationProvider elwasysAuthenticationProvider) {
        return new ProviderManager(List.<AuthenticationProvider>of(elwasysAuthenticationProvider));
    }

    @Bean
    @ConditionalOnWebApplication
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        http.authenticationManager(authenticationManager)
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll())
                .with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class)
                        .anyRequest(AuthorizedUrl::authenticated));
        return http.build();
    }
}
