package com.servidos.v1.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        // Orígenes explícitos (nunca "*" con credenciales: Spring lo rechaza).
        config.setAllowedOrigins(allowedOrigins);
        // Obligatorio: el refresh viaja en cookie HttpOnly + Authorization header.
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        // Solo /api/**: actuator y resto quedan fuera de CORS.
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
