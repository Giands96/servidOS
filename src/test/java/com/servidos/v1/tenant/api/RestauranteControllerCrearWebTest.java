package com.servidos.v1.tenant.api;

import com.servidos.v1.identity.application.usuario.RegistrarAdministradorRestauranteCommand;
import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.shared.exception.ConflictException;
import com.servidos.v1.shared.exception.GlobalExceptionHandler;
import com.servidos.v1.tenant.application.restaurante.CambiarEstadoRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteCommand;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.ListarRestaurantesUseCase;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.RestauranteCreado;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.application.suscripcion.GestionarSuscripcionUseCase;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Restaurante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato HTTP de POST /api/v1/restaurantes: validación Jakarta (422), mapeo de errores
 * de negocio y forma de la respuesta. Sin Spring Security: el @PreAuthorize no corre en standalone.
 */
@ExtendWith(MockitoExtension.class)
class RestauranteControllerCrearWebTest {

    private static final String PASSWORD = "secreta123";

    @Mock CrearRestauranteUseCase crearRestauranteUseCase;
    @Mock ObtenerRestauranteUseCase obtenerRestauranteUseCase;
    @Mock ListarRestaurantesUseCase listarRestaurantesUseCase;
    @Mock GestionarSuscripcionUseCase gestionarSuscripcionUseCase;
    @Mock CambiarPlanUseCase cambiarPlanUseCase;
    @Mock RenovarSuscripcionUseCase renovarSuscripcionUseCase;
    @Mock CambiarEstadoRestauranteUseCase cambiarEstadoRestauranteUseCase;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new RestauranteController(crearRestauranteUseCase, obtenerRestauranteUseCase,
                listarRestaurantesUseCase, gestionarSuscripcionUseCase, cambiarPlanUseCase,
                renovarSuscripcionUseCase, cambiarEstadoRestauranteUseCase);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String body(String administrador) {
        return """
                {"slug":"demo","nombre":"Demo","direccion":"Av. Lima 123","planId":9,"demo":false%s}
                """.formatted(administrador == null ? "" : ",\"administrador\":" + administrador);
    }

    private static String adminJson(String nombre, String apellido, String email, String password) {
        return """
                {"nombre":%s,"apellido":%s,"email":%s,"password":%s}
                """.formatted(q(nombre), q(apellido), q(email), q(password));
    }

    private static String q(String v) {
        return v == null ? "null" : "\"" + v + "\"";
    }

    private static RestauranteCreado creado() {
        return new RestauranteCreado(
                Restaurante.builder().restaurante_id(5L).slug("demo").nombre("Demo")
                        .direccion("Av. Lima 123").estado(EstadoRestaurante.ACTIVO).build(),
                Usuario.builder().usuario_id(77L).nombre("Ana").apellido("Paz").email("ana@demo.pe").build());
    }

    @Test
    void creaRestauranteConAdministradorYDa201SinPassword() throws Exception {
        when(crearRestauranteUseCase.ejecutar(any())).thenReturn(creado());

        var resultado = mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(adminJson("Ana", "Paz", "ana@demo.pe", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restauranteId").value(5))
                .andExpect(jsonPath("$.slug").value("demo"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.administrador.usuarioId").value(77))
                .andExpect(jsonPath("$.administrador.email").value("ana@demo.pe"))
                .andExpect(jsonPath("$.administrador.nombre").value("Ana"))
                .andExpect(jsonPath("$.administrador.password").doesNotExist())
                .andExpect(jsonPath("$.administrador.passwordHash").doesNotExist())
                .andReturn();

        assertFalse(resultado.getResponse().getContentAsString().contains(PASSWORD));

        var captor = ArgumentCaptor.forClass(CrearRestauranteCommand.class);
        verify(crearRestauranteUseCase).ejecutar(captor.capture());
        assertEquals(new RegistrarAdministradorRestauranteCommand("Ana", "Paz", "ana@demo.pe", PASSWORD),
                captor.getValue().administrador());
    }

    @Test
    void sinAdministradorDa422() throws Exception {
        mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrador")));

        verifyNoInteractions(crearRestauranteUseCase);
    }

    @Test
    void emailInvalidoDa422ConElCampoAnidado() throws Exception {
        mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(adminJson("Ana", "Paz", "no-es-email", PASSWORD))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrador.email")));

        verify(crearRestauranteUseCase, never()).ejecutar(any());
    }

    @Test
    void passwordCortoDa422SinEcoDelPassword() throws Exception {
        var resultado = mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(adminJson("Ana", "Paz", "ana@demo.pe", "corto"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrador.password")))
                .andReturn();

        assertFalse(resultado.getResponse().getContentAsString().contains("corto\""));
        verifyNoInteractions(crearRestauranteUseCase);
    }

    @Test
    void nombreDeUnaLetraDa422() throws Exception {
        mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(adminJson("A", "Paz", "ana@demo.pe", PASSWORD))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("administrador.nombre")));
    }

    @Test
    void apellidoVacioYEmailMuyLargoDan422() throws Exception {
        String emailLargo = "a".repeat(150) + "@demo.pe";
        var resultado = mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(adminJson("Ana", "", emailLargo, PASSWORD))))
                .andExpect(status().isUnprocessableEntity())
                .andReturn();

        String mensaje = resultado.getResponse().getContentAsString();
        assertTrue(mensaje.contains("administrador.apellido"));
        assertTrue(mensaje.contains("administrador.email"));
    }

    @Test
    void emailYaRegistradoDa409() throws Exception {
        when(crearRestauranteUseCase.ejecutar(any())).thenThrow(new ConflictException("El email ya existe"));

        mvc.perform(post("/api/v1/restaurantes").contentType(MediaType.APPLICATION_JSON)
                        .content(body(adminJson("Ana", "Paz", "ana@demo.pe", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El email ya existe"));
    }

    @Test
    void elRequestNoExponeElPasswordEnToString() {
        var request = new com.servidos.v1.tenant.api.dto.AdministradorRequest("Ana", "Paz", "ana@demo.pe", PASSWORD);

        assertFalse(request.toString().contains(PASSWORD));
    }
}
