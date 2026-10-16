package com.servidos.v1.tenant;

import com.servidos.v1.identity.application.usuario.CrearAdministradorRestauranteUseCase;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de restaurante con su dueño, por HTTP y contra Postgres. Sin @Transactional a
 * propósito: cada request confirma o deshace su propia transacción, que es lo que se quiere
 * comprobar (no queda restaurante sin dueño ni dueño sin restaurante). Se limpia a mano.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CrearRestauranteConDuenoIntegracionTest {

    static final String PASSWORD = "Clave-Secreta-9137";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanJpaRepository planRepository;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @MockitoSpyBean CrearAdministradorRestauranteUseCase crearAdministrador;

    String plataforma;
    Long planId;
    String slug;
    String email;

    @BeforeEach
    void setUp() {
        plataforma = "Bearer " + jwtService.generate(1L, null, "SUPERADMIN");
        planId = planRepository.findAll().stream()
                .filter(p -> Plan.EstadoPlan.ACTIVO.name().equals(p.getEstado()))
                .findFirst().orElseThrow().getPlanId();
        long n = System.nanoTime();
        slug = "it-dueno-" + n;
        email = "dueno-" + n + "@chifa.pe";
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM refresh_token WHERE usuario_id IN (SELECT usuario_id FROM usuario WHERE email = ?)", email);
        jdbc.update("DELETE FROM usuario_restaurante WHERE usuario_id IN (SELECT usuario_id FROM usuario WHERE email = ?)", email);
        jdbc.update("DELETE FROM usuario WHERE email = ?", email);
        jdbc.update("DELETE FROM suscripcion WHERE restaurante_id IN (SELECT restaurante_id FROM restaurante WHERE slug LIKE 'it-dueno-%')");
        jdbc.update("DELETE FROM restaurante WHERE slug LIKE 'it-dueno-%'");
    }

    private String cuerpo(String slug, String emailDueno) {
        return """
                {"slug":"%s","nombre":"Chifa Wa Lok","direccion":"Av. Grau 340","planId":%d,"demo":true,"demoDias":14,
                 "administrador":{"nombre":"Rosa","apellido":"Quispe","email":"%s","password":"%s"}}
                """.formatted(slug, planId, emailDueno, PASSWORD);
    }

    private org.springframework.test.web.servlet.ResultActions crear(String body) throws Exception {
        return mvc.perform(post("/api/v1/restaurantes").header("Authorization", plataforma)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void altaFelizCreaRestauranteYDuenoQuePuedeEntrar(CapturedOutput output) throws Exception {
        var resp = crear(cuerpo(slug, email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.administrador.email").value(email))
                .andExpect(jsonPath("$.administrador.nombre").value("Rosa"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(resp.contains(PASSWORD), "la respuesta no puede traer el password");
        assertFalse(resp.contains("password"), "ni siquiera el campo");
        Long restauranteId = json.readTree(resp).get("restauranteId").asLong();

        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("accessToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restauranteId").value(restauranteId))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.email").value(email));

        assertFalse(output.getAll().contains(PASSWORD), "el password no puede aparecer en los logs");
    }

    @Test
    void emailRepetidoEs409YNoCreaElRestaurante() throws Exception {
        crear(cuerpo(slug, email)).andExpect(status().isCreated());
        String otroSlug = slug + "-b";

        crear(cuerpo(otroSlug, email.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El email ya existe"));

        assertFalse(restauranteRepository.existsBySlug(otroSlug));
    }

    @Test
    void slugRepetidoEs400YNoCreaElUsuario() throws Exception {
        crear(cuerpo(slug, email)).andExpect(status().isCreated());
        String otroEmail = "otro-" + email;
        jdbc.update("DELETE FROM usuario WHERE email = ?", otroEmail);

        crear(cuerpo(slug, otroEmail)).andExpect(status().isBadRequest());

        assertFalse(usuarioRepository.existsByEmail(otroEmail));
    }

    @Test
    void sinAdministradorEs422() throws Exception {
        crear("""
                {"slug":"%s","nombre":"Chifa","planId":%d}
                """.formatted(slug, planId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrador")));
        assertFalse(restauranteRepository.existsBySlug(slug));
    }

    @Test
    void emailInvalidoEs422ConElCampoAnidado() throws Exception {
        crear(cuerpo(slug, "no-es-email"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrador.email")));
        assertFalse(restauranteRepository.existsBySlug(slug));
    }

    /** Si crear el dueño falla después de guardar el restaurante, el rollback lo deshace todo. */
    @Test
    void fallaAlCrearElDuenoNoDejaRestauranteSinDueno() throws Exception {
        doThrow(new IllegalStateException("falla simulada")).when(crearAdministrador).ejecutar(any(), any());

        crear(cuerpo(slug, email)).andExpect(status().is5xxServerError());

        assertFalse(restauranteRepository.existsBySlug(slug), "el restaurante no puede quedar sin dueño");
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM suscripcion s JOIN restaurante r USING (restaurante_id) WHERE r.slug = ?",
                Integer.class, slug));
        assertTrue(!usuarioRepository.existsByEmail(email));
    }
}
