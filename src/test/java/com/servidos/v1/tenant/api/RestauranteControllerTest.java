package com.servidos.v1.tenant.api;

import com.servidos.v1.shared.security.TenantContext;
import com.servidos.v1.tenant.api.dto.CambiarPlanRequest;
import com.servidos.v1.tenant.api.dto.CrearRestauranteRequest;
import com.servidos.v1.tenant.application.restaurante.CrearRestauranteUseCase;
import com.servidos.v1.tenant.application.restaurante.ObtenerRestauranteUseCase;
import com.servidos.v1.tenant.application.suscripcion.CambiarPlanUseCase;
import com.servidos.v1.tenant.application.suscripcion.GestionarSuscripcionUseCase;
import com.servidos.v1.tenant.application.suscripcion.RenovarSuscripcionUseCase;
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
    @Mock GestionarSuscripcionUseCase gestionarSuscripcionUseCase;
    @Mock CambiarPlanUseCase cambiarPlanUseCase;
    @Mock RenovarSuscripcionUseCase renovarSuscripcionUseCase;

    RestauranteController controller;

    @BeforeEach
    void setUp() {
        controller = new RestauranteController(crearRestauranteUseCase, obtenerRestauranteUseCase,
                gestionarSuscripcionUseCase, cambiarPlanUseCase, renovarSuscripcionUseCase);
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
    void cambiarPlanDelegaYDa200() {
        when(cambiarPlanUseCase.ejecutar(any())).thenReturn(
                com.servidos.v1.tenant.domain.Suscripcion.builder().suscripcion_id(2L)
                        .restaurante_id(100L).plan_id(4L)
                        .estado(com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion.ACTIVA)
                        .fecha_inicio(java.time.LocalDate.now())
                        .fecha_fin(java.time.LocalDate.now().plusDays(30)).build());
        var resp = controller.cambiarPlan(new CambiarPlanRequest(4L));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(4L, resp.getBody().planId());
    }

    @Test
    void renovarDelegaYDa200() {
        when(renovarSuscripcionUseCase.ejecutar(any())).thenReturn(
                com.servidos.v1.tenant.domain.Suscripcion.builder().suscripcion_id(3L)
                        .restaurante_id(100L).plan_id(4L)
                        .estado(com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion.ACTIVA)
                        .fecha_inicio(java.time.LocalDate.now())
                        .fecha_fin(java.time.LocalDate.now().plusMonths(1)).build());
        var resp = controller.renovar();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void cancelarDa200() {
        var resp = controller.cancelar();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        org.mockito.Mockito.verify(gestionarSuscripcionUseCase).cancelar(eq(100L));
    }
}
