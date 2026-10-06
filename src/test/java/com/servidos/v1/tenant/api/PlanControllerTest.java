package com.servidos.v1.tenant.api;

import com.servidos.v1.tenant.api.dto.PlanResponse;
import com.servidos.v1.tenant.application.plan.ListarPlanesUseCase;
import com.servidos.v1.tenant.domain.Plan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    @Mock ListarPlanesUseCase listarPlanesUseCase;

    @InjectMocks PlanController controller;

    @Test
    void listaPlanesDa200ConPrecioYMoneda() {
        when(listarPlanesUseCase.listar()).thenReturn(List.of(
                new Plan(1L, "Estándar", new BigDecimal("34.90"),
                        "Acceso completo", Plan.EstadoPlan.ACTIVO)));

        var resp = controller.listar();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
        PlanResponse p = resp.getBody().get(0);
        assertEquals(1L, p.planId());
        assertEquals("Estándar", p.nombre());
        assertEquals(0, new BigDecimal("34.90").compareTo(p.precio()));
        assertEquals("Acceso completo", p.descripcion());
        assertEquals("ACTIVO", p.estado());
        assertEquals("PEN", p.moneda());
    }

    @Test
    void catalogoVacioDa200ConListaVacia() {
        when(listarPlanesUseCase.listar()).thenReturn(List.of());

        var resp = controller.listar();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().isEmpty());
    }
}