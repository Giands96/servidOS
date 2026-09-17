package com.servidos.v1.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    private static HttpServletRequest request(String method, String path, String origin) {
        var req = new MockHttpServletRequest(method, path);
        req.addHeader("Origin", origin);
        return req;
    }

    @Test
    void apiExponeOrigenConfiguradoConCredenciales() {
        var source = new CorsConfig(List.of("http://localhost:4200")).corsConfigurationSource();

        var config = source.getCorsConfiguration(
                request("GET", "/api/v1/usuarios", "http://localhost:4200"));

        assertNotNull(config);
        assertEquals(List.of("http://localhost:4200"), config.getAllowedOrigins());
        assertTrue(config.getAllowCredentials());
        assertTrue(config.getAllowedMethods()
                .containsAll(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS")));
        assertTrue(config.getAllowedHeaders()
                .containsAll(List.of("Authorization", "Content-Type", "X-Requested-With")));
    }

    @Test
    void fueraDeApiNoHayCors() {
        var source = new CorsConfig(List.of("http://localhost:4200")).corsConfigurationSource();

        assertNull(source.getCorsConfiguration(
                request("GET", "/actuator/health", "http://localhost:4200")));
    }
}
