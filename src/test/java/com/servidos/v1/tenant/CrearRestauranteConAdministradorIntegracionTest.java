package com.servidos.v1.tenant;

import com.jayway.jsonpath.JsonPath;
import com.servidos.v1.identity.domain.EstadoUsuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * POST /api/v1/restaurantes de punta a punta: el dueño creado junto al restaurante puede entrar,
 * y los rechazos (email o slug repetido, datos inválidos) no dejan datos a medias.
 */
class CrearRestauranteConAdministradorIntegracionTest extends CrearRestauranteConAdministradorTestSupport {

    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void creaRestauranteSuscripcionYDuenoQuePuedeIniciarSesion() throws Exception {
        String slug = nuevoSlug();
        String email = nuevoEmail();

        var resp = crearRestaurante(bodyCrearRestaurante(slug, email, PASSWORD));

        assertEquals(201, resp.statusCode(), resp.body());
        assertFalse(resp.body().contains(PASSWORD), "la respuesta no puede llevar el password");
        Long restauranteId = JsonPath.<Number>read(resp.body(), "$.restauranteId").longValue();
        Long usuarioId = JsonPath.<Number>read(resp.body(), "$.administrador.usuarioId").longValue();
        assertEquals(slug, JsonPath.<String>read(resp.body(), "$.slug"));
        assertEquals(email, JsonPath.<String>read(resp.body(), "$.administrador.email"));

        var usuario = usuarioRepository.findByEmail(email).orElseThrow();
        assertEquals(usuarioId, usuario.getUsuarioId());
        assertEquals(EstadoUsuario.ACTIVO, usuario.getEstado());
        assertTrue(passwordEncoder.matches(PASSWORD, usuario.getPasswordHash()), "se guarda el hash BCrypt del password");
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM suscripcion WHERE restaurante_id = ?", Integer.class, restauranteId));
        assertEquals("ADMINISTRADOR", jdbc.queryForObject(
                "SELECT r.nombre FROM usuario_restaurante ur "
                        + "JOIN rol_restaurante r ON r.rol_restaurante_id = ur.rol_restaurante_id "
                        + "WHERE ur.usuario_id = ? AND ur.restaurante_id = ?",
                String.class, usuarioId, restauranteId));

        var login = post("/api/v1/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD), null);
        assertEquals(200, login.statusCode(), login.body());
        String accessToken = JsonPath.read(login.body(), "$.accessToken");

        var me = get("/api/v1/auth/me", accessToken);
        assertEquals(200, me.statusCode(), me.body());
        assertEquals(restauranteId, JsonPath.<Number>read(me.body(), "$.restauranteId").longValue());
        assertEquals("ADMINISTRADOR", JsonPath.<String>read(me.body(), "$.rol"));
    }

    @Test
    void emailRepetidoDa409YNoCreaElRestaurante() throws Exception {
        String email = nuevoEmail();
        assertEquals(201, crearRestaurante(bodyCrearRestaurante(nuevoSlug(), email, PASSWORD)).statusCode());

        String slugRechazado = nuevoSlug();
        // Mismo email con otra capitalización: el email es único sin distinguir mayúsculas.
        var resp = crearRestaurante(bodyCrearRestaurante(slugRechazado, email.toUpperCase(), PASSWORD));

        assertEquals(409, resp.statusCode(), resp.body());
        assertTrue(resp.body().contains("El email ya existe"), resp.body());
        assertFalse(restauranteRepository.existsBySlug(slugRechazado), "no debe quedar el restaurante");
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE lower(email) = ?", Integer.class, email));
    }

    @Test
    void slugRepetidoDa400YNoCreaAlUsuario() throws Exception {
        String slug = nuevoSlug();
        assertEquals(201, crearRestaurante(bodyCrearRestaurante(slug, nuevoEmail(), PASSWORD)).statusCode());

        String emailRechazado = nuevoEmail();
        var resp = crearRestaurante(bodyCrearRestaurante(slug, emailRechazado, PASSWORD));

        assertEquals(400, resp.statusCode(), resp.body());
        assertTrue(resp.body().contains("El slug ya existe"), resp.body());
        assertFalse(usuarioRepository.existsByEmail(emailRechazado), "no debe quedar el usuario");
    }

    @Test
    void sinAdministradorOConEmailInvalidoDa422() throws Exception {
        String sinAdministrador = """
                {"slug":"%s","nombre":"Restaurante IT","planId":%d}
                """.formatted(nuevoSlug(), planActivoId());
        var faltante = crearRestaurante(sinAdministrador);
        assertEquals(422, faltante.statusCode(), faltante.body());
        assertTrue(faltante.body().contains("administrador"), faltante.body());

        String slug = nuevoSlug();
        var invalido = crearRestaurante(bodyCrearRestaurante(slug, "no-es-un-email", PASSWORD));
        assertEquals(422, invalido.statusCode(), invalido.body());
        assertTrue(invalido.body().contains("administrador.email"), invalido.body());
        assertFalse(restauranteRepository.existsBySlug(slug));
    }
}
