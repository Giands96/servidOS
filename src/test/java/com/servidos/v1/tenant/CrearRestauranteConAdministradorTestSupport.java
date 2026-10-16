package com.servidos.v1.tenant;

import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Base de los tests de punta a punta de POST /api/v1/restaurantes: servidor real, Postgres real
 * y HTTP real. A propósito NO es @Transactional: el commit/rollback tiene que ser el del caso de
 * uso, no el de un test que lo envuelve. Por eso los datos se borran a mano al terminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class CrearRestauranteConAdministradorTestSupport {

    static final String PASSWORD = "secreta123";

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanJpaRepository planRepository;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<String> slugs = new ArrayList<>();
    private final List<String> emails = new ArrayList<>();

    @AfterEach
    void limpiarDatosCreados() {
        for (String email : emails) {
            String dueno = "(SELECT usuario_id FROM usuario WHERE email = ?)";
            jdbc.update("DELETE FROM refresh_token WHERE usuario_id IN " + dueno, email);
            jdbc.update("DELETE FROM usuario_restaurante WHERE usuario_id IN " + dueno, email);
            jdbc.update("DELETE FROM usuario WHERE email = ?", email);
        }
        for (String slug : slugs) {
            jdbc.update("DELETE FROM suscripcion WHERE restaurante_id IN "
                    + "(SELECT restaurante_id FROM restaurante WHERE slug = ?)", slug);
            jdbc.update("DELETE FROM restaurante WHERE slug = ?", slug);
        }
    }

    /** Slug único por corrida; queda registrado para limpiarlo. */
    String nuevoSlug() {
        String slug = "it-onb-" + System.nanoTime();
        slugs.add(slug);
        return slug;
    }

    /** Email único por corrida; queda registrado para limpiarlo. */
    String nuevoEmail() {
        String email = "it-onb-" + System.nanoTime() + "@test.local";
        emails.add(email);
        return email;
    }

    Long planActivoId() {
        return planRepository.findAll().stream()
                .filter(p -> "ACTIVO".equals(p.getEstado()))
                .map(PlanJpaEntity::getPlanId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay un plan ACTIVO sembrado en la BD de test"));
    }

    /** JWT de plataforma (sin tenant): pasa el @PreAuthorize del alta de restaurantes. */
    String tokenSuperadmin() {
        return jwtService.generate(1L, null, "SUPERADMIN");
    }

    String bodyCrearRestaurante(String slug, String email, String password) {
        return """
                {"slug":"%s","nombre":"Restaurante IT","planId":%d,"demo":false,
                 "administrador":{"nombre":"Ana","apellido":"Paz","email":"%s","password":"%s"}}
                """.formatted(slug, planActivoId(), email, password);
    }

    HttpResponse<String> crearRestaurante(String json) throws IOException, InterruptedException {
        return post("/api/v1/restaurantes", json, tokenSuperadmin());
    }

    HttpResponse<String> post(String path, String json, String bearer) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path, String bearer) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
