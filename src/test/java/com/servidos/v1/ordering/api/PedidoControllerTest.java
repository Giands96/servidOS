package com.servidos.v1.ordering.api;

import com.servidos.v1.ordering.api.dto.CambiarEstadoRequest;
import com.servidos.v1.ordering.api.dto.CrearPedidoItemRequest;
import com.servidos.v1.ordering.api.dto.CrearPedidoRequest;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.mesa.NumerosDeMesa;
import com.servidos.v1.ordering.application.pedido.ListarPedidosUseCase;
import com.servidos.v1.ordering.application.pedido.ConfirmarPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock CrearPedidoUseCase crearPedidoUseCase;
    @Mock ConfirmarPedidoUseCase confirmarPedidoUseCase;
    @Mock CambiarEstadoPedidoUseCase cambiarEstadoPedidoUseCase;
    @Mock ListarPedidosUseCase listarPedidosUseCase;
    @Mock NumerosDeMesa numerosDeMesa;

    PedidoController controller;

    @BeforeEach
    void setUp() {
        controller = new PedidoController(crearPedidoUseCase, confirmarPedidoUseCase, cambiarEstadoPedidoUseCase,
                listarPedidosUseCase, numerosDeMesa);
        TenantContext.setRestauranteId(100L);
        CurrentUser.setCurrentUser(9L);
        CurrentUser.setRole("MESERO");
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        CurrentUser.clear();
    }

    private static Pedido pedido(EstadoPedido estado) {
        return Pedido.builder().pedido_id(1L).restaurante_id(100L).mesa_id(3L)
                .tipoPedido(TipoPedido.MESA).estado(estado)
                .total(new BigDecimal("50.00")).build();
    }

    @Test
    void crearDelegaYDa201() {
        when(crearPedidoUseCase.ejecutar(any(), eq(100L), eq(9L), eq("MESERO"))).thenReturn(pedido(EstadoPedido.PENDIENTE));

        var resp = controller.crear(new CrearPedidoRequest(TipoPedido.MESA, 3L, null, null,
                List.of(new CrearPedidoItemRequest(7L, 2, null))));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(1L, resp.getBody().pedidoId());
        assertEquals(EstadoPedido.PENDIENTE, resp.getBody().estado());
    }

    @Test
    void confirmarDelegaYDa200() {
        when(confirmarPedidoUseCase.ejecutar(1L, 100L)).thenReturn(pedido(EstadoPedido.EN_PREPARACION));

        var resp = controller.confirmar(1L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(EstadoPedido.EN_PREPARACION, resp.getBody().estado());
        verify(confirmarPedidoUseCase).ejecutar(1L, 100L);
    }

    @Test
    void cambiarEstadoPasaElRolDelJwtYDa200() {
        when(cambiarEstadoPedidoUseCase.ejecutar(1L, 100L, EstadoPedido.LISTO, "MESERO"))
                .thenReturn(pedido(EstadoPedido.LISTO));

        var resp = controller.cambiarEstado(1L, new CambiarEstadoRequest(EstadoPedido.LISTO));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(EstadoPedido.LISTO, resp.getBody().estado());
    }

    @Test
    void listarUsaElTenantDelJwtYDevuelveElEstadoDePago() {
        var pedido = com.servidos.v1.ordering.domain.Pedido.builder()
                .pedido_id(7L).tipoPedido(com.servidos.v1.ordering.domain.TipoPedido.RECOJO)
                .estado(com.servidos.v1.ordering.domain.EstadoPedido.ENTREGADO)
                .total(new java.math.BigDecimal("10.00")).build();
        when(listarPedidosUseCase.listar(100L, null, null, true, 0, 20)).thenReturn(
                new org.springframework.data.domain.PageImpl<>(
                        java.util.List.of(new ListarPedidosUseCase.PedidoListado(pedido, "PAGADO", 4))));

        var resp = controller.listar(null, null, true, 0, 20);

        var fila = resp.getBody().getContent().get(0);
        assertEquals(7L, fila.pedidoId());
        assertEquals("PAGADO", fila.estadoPago());
        assertEquals(4, fila.numeroMesa());
    }

    private static String permisos(String metodo, Class<?>... tipos) throws NoSuchMethodException {
        Method m = PedidoController.class.getMethod(metodo, tipos);
        return m.getAnnotation(PreAuthorize.class).value();
    }

    @Test
    void elMeseroTomaConfirmaYCambiaEstadoJuntoARecepcion() throws Exception {
        String salon = "hasAnyRole('ADMINISTRADOR','RECEPCION','MESERO')";
        assertEquals(salon, permisos("crear", CrearPedidoRequest.class));
        assertEquals(salon, permisos("confirmar", Long.class));
        assertEquals(salon, permisos("cambiarEstado", Long.class, CambiarEstadoRequest.class));
    }

    @Test
    void elMeseroLeeElListadoJuntoALaCaja() throws Exception {
        assertEquals("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO','MESERO')",
                permisos("listar", EstadoPedido.class, LocalDate.class, boolean.class, int.class, int.class));
    }

    private static String permisos(String metodo, Class<?>... tipos) throws NoSuchMethodException {
        Method m = PedidoController.class.getMethod(metodo, tipos);
        return m.getAnnotation(PreAuthorize.class).value();
    }

    @Test
    void elMeseroTomaConfirmaYCambiaEstadoJuntoARecepcion() throws Exception {
        String salon = "hasAnyRole('ADMINISTRADOR','RECEPCION','MESERO')";
        assertEquals(salon, permisos("crear", CrearPedidoRequest.class));
        assertEquals(salon, permisos("confirmar", Long.class));
        assertEquals(salon, permisos("cambiarEstado", Long.class, CambiarEstadoRequest.class));
    }

    @Test
    void elMeseroLeeElListadoJuntoALaCaja() throws Exception {
        assertEquals("hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO','MESERO')",
                permisos("listar", EstadoPedido.class, LocalDate.class, boolean.class, int.class, int.class));
    }
}
