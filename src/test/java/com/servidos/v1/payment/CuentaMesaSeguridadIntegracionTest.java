package com.servidos.v1.payment;

import com.servidos.v1.identity.infrastructure.security.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
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
 * Los permisos de la cuenta de mesa con HTTP real, pasando por el TenantFilter y la seguridad de
 * métodos. Un rol de caja pasa la seguridad y recién el caso de uso rechaza la mesa inexistente (400),
 * así se distingue "sin permiso" (403) de "permitido". No siembra ni escribe nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CuentaMesaSeguridadIntegracionTest {

    /** Tenant sin datos: la mesa no existe. */
    static final long RESTAURANTE = 999_999_999L;

    @LocalServerPort int port;
    @Autowired JwtService jwtService;

    private final HttpClient http = HttpClient.newHttpClient();

    @ParameterizedTest
    @ValueSource(strings = {"MESERO", "COCINERO", "REPARTIDOR"})
    void losRolesSinPermisoDeCajaReciben403AlConsultar(String rol) throws Exception {
        assertEquals(403, get("/api/v1/mesas/1/cuenta", jwtService.generate(1L, RESTAURANTE, rol)).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "RECEPCION", "CAJERO"})
    void losRolesDeCajaPasanLaSeguridadAlConsultar(String rol) throws Exception {
        assertEquals(400, get("/api/v1/mesas/1/cuenta", jwtService.generate(1L, RESTAURANTE, rol)).statusCode());
    }

    private HttpResponse<String> get(String path, String bearer) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
