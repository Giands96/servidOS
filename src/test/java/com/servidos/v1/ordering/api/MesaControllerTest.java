package com.servidos.v1.ordering.api;

import com.servidos.v1.ordering.api.dto.ActualizarMesaRequest;
import com.servidos.v1.ordering.api.dto.CrearMesaRequest;
import com.servidos.v1.ordering.api.dto.OcuparMesaRequest;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaCommand;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.LiberarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.ListarMesasUseCase;
import com.servidos.v1.ordering.application.mesa.OcuparMesaCommand;
import com.servidos.v1.ordering.application.mesa.OcuparMesaUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.shared.security.TenantContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MesaControllerTest {

    @Mock CrearMesaUseCase crearMesaUseCase;
    @Mock ListarMesasUseCase listarMesasUseCase;
    @Mock ActualizarMesaUseCase actualizarMesaUseCase;
    @Mock OcuparMesaUseCase ocuparMesaUseCase;
    @Mock LiberarMesaUseCase liberarMesaUseCase;

    MesaController controller;

    @BeforeEach
    void setUp() {
        controller = new MesaController(crearMesaUseCase, listarMesasUseCase, actualizarMesaUseCase,
                ocuparMesaUseCase, liberarMesaUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Mesa mesa(EstadoMesa estado, Integer comensales) {
        return Mesa.builder().mesa_id(7L).restaurante_id(100L).numero(5).capacidad(4)
                .comensales(comensales).estado(estado).build();
    }

    private static String permisos(String metodo, Class<?>... tipos) throws NoSuchMethodException {
        Method m = MesaController.class.getMethod(metodo, tipos);
        return m.getAnnotation(PreAuthorize.class).value();
    }

    @Test
    void crearDelegaConElTenantYDa201() {
        when(crearMesaUseCase.ejecutar(new CrearMesaCommand(5, 4), 100L)).thenReturn(mesa(EstadoMesa.LIBRE, null));

        var resp = controller.crear(new CrearMesaRequest(5, 4));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(7L, resp.getBody().mesaId());
        assertEquals(5, resp.getBody().numero());
        assertEquals(4, resp.getBody().capacidad());
        assertEquals(EstadoMesa.LIBRE, resp.getBody().estado());
        assertNull(resp.getBody().comensales());
    }

    @Test
    void listarSinFiltroDa200() {
        when(listarMesasUseCase.listar(100L, null))
                .thenReturn(List.of(mesa(EstadoMesa.LIBRE, null), mesa(EstadoMesa.OCUPADA, 3)));

        var resp = controller.listar(null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(2, resp.getBody().size());
        assertEquals(3, resp.getBody().get(1).comensales());
    }

    @Test
    void listarPasaElFiltroDeEstado() {
        when(listarMesasUseCase.listar(100L, EstadoMesa.OCUPADA)).thenReturn(List.of(mesa(EstadoMesa.OCUPADA, 3)));

        var resp = controller.listar(EstadoMesa.OCUPADA);

        assertEquals(1, resp.getBody().size());
        verify(listarMesasUseCase).listar(100L, EstadoMesa.OCUPADA);
    }

    @Test
    void actualizarDelegaConElIdDelPath() {
        when(actualizarMesaUseCase.ejecutar(new ActualizarMesaCommand(7L, 9, 8), 100L))
                .thenReturn(Mesa.builder().mesa_id(7L).restaurante_id(100L).numero(9).capacidad(8)
                        .estado(EstadoMesa.LIBRE).build());

        var resp = controller.actualizar(7L, new ActualizarMesaRequest(9, 8));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(9, resp.getBody().numero());
        assertEquals(8, resp.getBody().capacidad());
    }

    @Test
    void ocuparDelegaYDevuelveLaMesaOcupada() {
        when(ocuparMesaUseCase.ejecutar(new OcuparMesaCommand(7L, 3), 100L)).thenReturn(mesa(EstadoMesa.OCUPADA, 3));

        var resp = controller.ocupar(7L, new OcuparMesaRequest(3));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(EstadoMesa.OCUPADA, resp.getBody().estado());
        assertEquals(3, resp.getBody().comensales());
    }

    @Test
    void liberarDelegaYDevuelveLaMesaLibre() {
        when(liberarMesaUseCase.ejecutar(7L, 100L)).thenReturn(mesa(EstadoMesa.LIBRE, null));

        var resp = controller.liberar(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(EstadoMesa.LIBRE, resp.getBody().estado());
    }

    @Test
    void soloElAdministradorCreaYActualizaMesas() throws Exception {
        assertEquals("hasRole('ADMINISTRADOR')", permisos("crear", CrearMesaRequest.class));
        assertEquals("hasRole('ADMINISTRADOR')", permisos("actualizar", Long.class, ActualizarMesaRequest.class));
    }

    @Test
    void listarLoVeElPersonalDeSalaYCaja() throws Exception {
        assertEquals("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO','MESERO')",
                permisos("listar", EstadoMesa.class));
    }

    @Test
    void ocuparYLiberarLosHaceElPersonalDeSala() throws Exception {
        String sala = "hasAnyRole('ADMINISTRADOR','RECEPCION','MESERO')";
        assertEquals(sala, permisos("ocupar", Long.class, OcuparMesaRequest.class));
        assertEquals(sala, permisos("liberar", Long.class));
    }

    @Test
    void laValidacionExigeNumeroCapacidadYComensalesPositivos() {
        Validator v = Validation.buildDefaultValidatorFactory().getValidator();

        assertTrue(v.validate(new CrearMesaRequest(5, 4)).isEmpty());
        assertFalse(v.validate(new CrearMesaRequest(null, 4)).isEmpty());
        assertFalse(v.validate(new CrearMesaRequest(0, 4)).isEmpty());
        assertFalse(v.validate(new CrearMesaRequest(5, -1)).isEmpty());
        assertFalse(v.validate(new ActualizarMesaRequest(5, null)).isEmpty());
        assertFalse(v.validate(new OcuparMesaRequest(0)).isEmpty());
        assertFalse(v.validate(new OcuparMesaRequest(null)).isEmpty());
    }
}
