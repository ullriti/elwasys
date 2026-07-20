package org.kabieror.elwasys.backend.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadaten für die generierte OpenAPI-Beschreibung der Terminal-API (AP4, siehe
 * kb/05-migration-plan.md: "API-Dokumentation: springdoc-openapi ... oder gleichwertig").
 * Beschreibt zusätzlich das Bearer-Token-Auth-Schema (siehe
 * {@link org.kabieror.elwasys.backend.auth.terminal.TerminalTokenAuthenticationFilter}), damit
 * Swagger-UI ein "Authorize"-Feld für das Standort-Token anbietet.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "terminalToken";

    @Bean
    public OpenAPI elwasysTerminalApiOpenApi() {
        return new OpenAPI().info(new Info().title("elwasys Terminal-API")
                        .version("v1")
                        .description(
                                "REST-API + WebSocket-Fundament für Raspi-Terminals (Phase 2 AP4, siehe "
                                        + "kb/05-migration-plan.md und kb/03-modules.md). Authentifizierung über ein "
                                        + "statisches, rotierbares Standort-Token als 'Authorization: Bearer <token>' "
                                        + "-Header."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("opaque")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
