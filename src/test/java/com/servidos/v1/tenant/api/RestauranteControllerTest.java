package com.servidos.v1.tenant.api;

import com.servidos.v1.shared.security.TenantContext;
import com.servidos.v1.tenant.api.dto.CambiarPlanRequest;
import com.servidos.v1.tenant.api.dto.CambiarEstadoRestauranteRequest;
import com.servidos.v1.tenant.api.dto.CrearRestauranteRequest;
import com.servidos.v1.tenant.api.dto.RenovarSuscripcionRequest;
import com.servidos.v1.tenant.application.restaurante.CambiarEstadoRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.ListarRestaurantesUseCase;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.application.suscripcion.GestionarSuscripcionUseCase;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
import com.servidos.v1.tenant.application.suscripcion.SuscripcionConPlan;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Restaurante;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestauranteControllerTest {

    @Mock CrearRestauranteUseCase crearRestauranteUseCase;
    @Mock ObtenerRestauranteUseCase obtenerRestauranteUseCase;
    @Mock ListarRestaurantesUseCase listarRestaurantesUseCase;
    @Mock GestionarSuscripcionUseCase gestionarSuscripcionUseCase;
    @Mock CambiarPlanUseCase cambiarPlanUseCase;
    @Mock RenovarSuscripcionUseCase renovarSuscripcionUseCase;
    @Mock CambiarEstadoRestauranteUseCase cambiarEstadoRestauranteUseCase;

    RestauranteController controller;

    @BeforeEach
    void setUp() {
        controller = new RestauranteController(crearRestauranteUseCase, obtenerRestauranteUseCase,
                listarRestaurantesUseCase,
                gestionarSuscripcionUseCase, cambiarPlanUseCase, renovarSuscripcionUseCase,
                cambiarEstadoRestauranteUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void crearDemoDelegaYDa201() {
        when(crearRestauranteUseCase.ejecutar(any())).thenReturn(
                Restaurante.builder().restaurante_id(1L).slug("demo").nombre("Demo")
                        .estado(EstadoRestaurante.ACTIVO).build());
        var resp = controller.crear(new CrearRestauranteRequest("demo", "Demo", null, 9L, true, 10));
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("demo", resp.getBody().slug());
    }

    @Test
    void actualUsaTenantContextYDa200() {
        when(obtenerRestauranteUseCase.obtener(eq(100L))).thenReturn(
                Restaurante.builder().restaurante_id(100L).slug("demo").nombre("Demo")
                        .estado(EstadoRestaurante.ACTIVO).build());
        var resp = controller.actual();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(100L, resp.getBody().restauranteId());
    }

    @Test
    void cambiarEstadoDelegaElIdExplicitoYDa200() {
        when(cambiarEstadoRestauranteUseCase.ejecutar(eq(7L), eq(EstadoRestaurante.ACTIVO)))
                .thenReturn(Restaurante.builder().restaurante_id(7L).slug("demo").nombre("Demo")
                        .estado(EstadoRestaurante.ACTIVO).build());

        var resp = controller.cambiarEstado(7L,
                new CambiarEstadoRestauranteRequest(EstadoRestaurante.ACTIVO));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(7L, resp.getBody().restauranteId());
        assertEquals(EstadoRestaurante.ACTIVO, resp.getBody().estado());
    }

    /**
     * La suspensión tiene que viajar en la respuesta de `/restaurantes/actual` porque
     * es de ahí de donde el frontend saca el estado para decidir mostrar el modal.
     */
    @Test
    void actualExponeElEstadoParaQueElFrontendDetecteLaSuspension() {
        when(obtenerRestauranteUseCase.obtener(eq(100L))).thenReturn(
                Restaurante.builder().restaurante_id(100L).slug("demo").nombre("Demo")
                        .estado(EstadoRestaurante.INACTIVO).build());

        var resp = controller.actual();

        assertEquals(EstadoRestaurante.INACTIVO, resp.getBody().estado());
    }

    @Test
    void cambiarPlanDelegaYDa200() {
        when(cambiarPlanUseCase.ejecutar(any())).thenReturn(suscripcionConPlan(2L, 4L, "Estándar", 30));
        var resp = controller.cambiarPlan(new CambiarPlanRequest(4L, true, "secreto"));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(4L, resp.getBody().planId());
        assertEquals("Estándar", resp.getBody().nombrePlan());
        assertEquals(0, new java.math.BigDecimal("34.90").compareTo(resp.getBody().monto()));
        assertEquals("PEN", resp.getBody().moneda());
    }

    private SuscripcionConPlan suscripcionConPlan(Long id, Long planId, String nombrePlan, int dias) {
        var s = com.servidos.v1.tenant.domain.Suscripcion.builder().suscripcion_id(id)
                .restaurante_id(100L).plan_id(planId)
                .monto(new java.math.BigDecimal("34.90")).moneda("PEN")
                .estado(com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion.ACTIVA)
                .fecha_inicio(java.time.LocalDate.now())
                .fecha_fin(java.time.LocalDate.now().plusDays(dias)).build();
        return new SuscripcionConPlan(s, nombrePlan);
    }

    @Test
    void renovarDelegaYDa200() {
        when(renovarSuscripcionUseCase.ejecutar(any())).thenReturn(suscripcionConPlan(3L, 4L, "Estándar", 30));
        var resp = controller.renovar(new RenovarSuscripcionRequest("secreto"));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void cancelarDa200() {
        var resp = controller.cancelar();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        org.mockito.Mockito.verify(gestionarSuscripcionUseCase).cancelar(eq(100L));
    }

    @Test
    void listarTodosMapeaSuscripcionYDa200() {
        var fila = new com.servidos.v1.tenant.application.restaurante.RestauranteConSuscripcion(
                1L, "demo", "Demo", null, EstadoRestaurante.ACTIVO,
                5L, 9L, "Mensual",
                com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion.ACTIVA,
                java.time.LocalDate.now(), java.time.LocalDate.now().plusDays(30));
        when(listarRestaurantesUseCase.listar(any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(fila)));
        var resp = controller.listarTodos(org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getTotalElements());
        assertEquals("Mensual", resp.getBody().getContent().get(0).suscripcion().nombrePlan());
    }

    @Test
    void listarTodosSinSuscripcionMapeaNull() {
        var fila = new com.servidos.v1.tenant.application.restaurante.RestauranteConSuscripcion(
                2L, "nuevo", "Nuevo", null, EstadoRestaurante.ACTIVO,
                null, null, null, null, null, null);
        when(listarRestaurantesUseCase.listar(any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(fila)));
        var resp = controller.listarTodos(org.springframework.data.domain.PageRequest.of(0, 10));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(null, resp.getBody().getContent().get(0).suscripcion());
    }
}
