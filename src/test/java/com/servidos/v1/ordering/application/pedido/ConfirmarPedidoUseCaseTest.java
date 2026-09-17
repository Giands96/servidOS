package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmarPedidoUseCaseTest {

    @Mock
    CambiarEstadoPedidoUseCase cambiarEstado;

    ConfirmarPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmarPedidoUseCase(cambiarEstado);
    }

    private static Pedido pedidoEnPreparacion() {
        return Pedido.crear(10L, null, 3L, TipoPedido.MESA, null, null,
                EstadoPedido.EN_PREPARACION, BigDecimal.ZERO);
    }

    @Test
    void confirmaDelegandoTransicionAPreparacion() {
        when(cambiarEstado.ejecutar(1L, 10L, EstadoPedido.EN_PREPARACION))
                .thenReturn(pedidoEnPreparacion());

        var confirmado = useCase.ejecutar(1L, 10L);

        assertEquals(EstadoPedido.EN_PREPARACION, confirmado.getEstado());
        verify(cambiarEstado).ejecutar(1L, 10L, EstadoPedido.EN_PREPARACION);
    }

    @Test
    void noConfirmadoPropagaErrorDeTransicion() {
        when(cambiarEstado.ejecutar(1L, 10L, EstadoPedido.EN_PREPARACION))
                .thenThrow(new BusinessException("Transición no permitida"));

        assertThrows(BusinessException.class, () -> useCase.ejecutar(1L, 10L));
    }

    @Test
    void exigeIdentificadores() {
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(null, 10L));
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, null));
    }
}
