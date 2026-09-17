package com.servidos.v1.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI servidosOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ServidOS API")
                        .version("v1")
                        .description("API REST multi-tenant para gestión de restaurantes. "
                                + "El `restauranteId` siempre se deriva del JWT vía TenantContext, nunca del JSON."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .tags(List.of(
                        new Tag().name("Auth").description("Login, refresh, logout y sesión actual"),
                        new Tag().name("Usuarios").description("Registro y gestión de roles por tenant")));
    }
}
