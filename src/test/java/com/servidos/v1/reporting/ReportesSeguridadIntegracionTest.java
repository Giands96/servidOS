package com.servidos.v1.reporting;

import com.servidos.v1.identity.infrastructure.security.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Los reportes son solo del ADMINISTRADOR: se prueba con HTTP real, pasando por el TenantFilter y la
 * seguridad de métodos, no leyendo la anotación. Solo lee datos, así que no siembra ni limpia nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportesSeguridadIntegracionTest {

    /** Tenant sin datos: el administrador recibe reportes vacíos. */
    static final long RESTAURANTE = 999_999_999L;

    @LocalServerPort int port;
    @Autowired JwtService jwtService;

    private final HttpClient http = HttpClient.newHttpClient();

    @ParameterizedTest
    @CsvSource({
            "RECEPCION, /api/v1/reportes/ventas",
            "CAJERO, /api/v1/reportes/ventas",
            "MESERO, /api/v1/reportes/productos",
            "COCINERO, /api/v1/reportes/productos",
            "REPARTIDOR, /api/v1/reportes/ventas/export?formato=PDF",
            "CAJERO, /api/v1/reportes/productos/export?formato=XLSX"
    })
    void rolesQueNoSonAdministradorReciben403(String rol, String path) throws Exception {
        assertEquals(403, get(path, jwtService.generate(1L, RESTAURANTE, rol)).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/reportes/ventas?periodo=SEMANAL", "/api/v1/reportes/productos?periodo=MENSUAL"})
    void administradorPuedeVerLosReportes(String path) throws Exception {
        assertEquals(200, get(path, jwtService.generate(1L, RESTAURANTE, "ADMINISTRADOR")).statusCode());
    }

    private HttpResponse<String> get(String path, String bearer) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
