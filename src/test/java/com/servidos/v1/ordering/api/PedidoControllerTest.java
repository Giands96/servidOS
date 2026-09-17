package com.servidos.v1.ordering.api;

import com.servidos.v1.ordering.api.dto.CambiarEstadoRequest;
import com.servidos.v1.ordering.api.dto.CrearPedidoItemRequest;
import com.servidos.v1.ordering.api.dto.CrearPedidoRequest;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.ConfirmarPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
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

    PedidoController controller;

    @BeforeEach
    void setUp() {
        controller = new PedidoController(crearPedidoUseCase, confirmarPedidoUseCase, cambiarEstadoPedidoUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Pedido pedido(EstadoPedido estado) {
        return Pedido.builder().pedido_id(1L).restaurante_id(100L).mesa_id(3L)
                .tipoPedido(TipoPedido.MESA).estado(estado)
                .total(new BigDecimal("50.00")).build();
    }

    @Test
    void crearDelegaYDa201() {
        when(crearPedidoUseCase.ejecutar(any(), eq(100L))).thenReturn(pedido(EstadoPedido.PENDIENTE));

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
    void cambiarEstadoDelegaYDa200() {
        when(cambiarEstadoPedidoUseCase.ejecutar(1L, 100L, EstadoPedido.LISTO))
                .thenReturn(pedido(EstadoPedido.LISTO));

        var resp = controller.cambiarEstado(1L, new CambiarEstadoRequest(EstadoPedido.LISTO));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(EstadoPedido.LISTO, resp.getBody().estado());
    }
}
