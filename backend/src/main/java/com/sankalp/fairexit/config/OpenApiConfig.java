package com.sankalp.fairexit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI with an "Authorize" button: paste the accessToken from /api/auth/login. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fairExitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FairExit API")
                        .version("0.1.0")
                        .description("Audits a landlord's security deposit deductions against the rent agreement, "
                                + "wear and tear rules, move in evidence and real repair prices. "
                                + "This is information, not legal advice."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
