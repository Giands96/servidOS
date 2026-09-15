package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CambiarEstadoPedidoUseCaseTest {

    @Mock
    PedidoJpaRepository pedidoRepository;

    CambiarEstadoPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CambiarEstadoPedidoUseCase(pedidoRepository, new PedidoMapper());
    }

    private PedidoJpaEntity pedido(EstadoPedido estado, TipoPedido tipo) {
        PedidoJpaEntity e = new PedidoJpaEntity();
        e.setPedidoId(1L);
        e.setRestauranteId(10L);
        e.setTipoPedido(tipo);
        e.setEstado(estado);
        e.setTotal(BigDecimal.ZERO);
        return e;
    }

    private void dadoPedido(PedidoJpaEntity e) {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(1L, 10L))
                .thenReturn(Optional.of(e));
    }

    private void dadoSave() {
        when(pedidoRepository.save(any(PedidoJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void mesaAvanzaPendienteAPreparacionAListoAEntregado() {
        dadoSave();
        dadoPedido(pedido(EstadoPedido.PENDIENTE, TipoPedido.MESA));
        assertEquals(EstadoPedido.EN_PREPARACION,
                useCase.ejecutar(1L, 10L, EstadoPedido.EN_PREPARACION).getEstado());

        dadoPedido(pedido(EstadoPedido.EN_PREPARACION, TipoPedido.MESA));
        assertEquals(EstadoPedido.LISTO,
                useCase.ejecutar(1L, 10L, EstadoPedido.LISTO).getEstado());

        dadoPedido(pedido(EstadoPedido.LISTO, TipoPedido.MESA));
        assertEquals(EstadoPedido.ENTREGADO,
                useCase.ejecutar(1L, 10L, EstadoPedido.ENTREGADO).getEstado());
    }

    @Test
    void deliveryPasaPorEnEntregaAntesDeEntregado() {
        dadoSave();
        dadoPedido(pedido(EstadoPedido.LISTO, TipoPedido.DELIVERY));
        assertEquals(EstadoPedido.EN_ENTREGA,
                useCase.ejecutar(1L, 10L, EstadoPedido.EN_ENTREGA).getEstado());

        dadoPedido(pedido(EstadoPedido.EN_ENTREGA, TipoPedido.DELIVERY));
        assertEquals(EstadoPedido.ENTREGADO,
                useCase.ejecutar(1L, 10L, EstadoPedido.ENTREGADO).getEstado());
    }

    @Test
    void deliveryNoPuedeSaltarDeListoAEntregado() {
        dadoPedido(pedido(EstadoPedido.LISTO, TipoPedido.DELIVERY));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 10L, EstadoPedido.ENTREGADO));
        assertTrue(ex.getMessage().contains("no permitida"));
    }

    @Test
    void localNoPuedePasarPorEnEntrega() {
        dadoPedido(pedido(EstadoPedido.LISTO, TipoPedido.MESA));
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 10L, EstadoPedido.EN_ENTREGA));
    }

    @Test
    void estadosTerminalesNoAdmitenCambios() {
        dadoPedido(pedido(EstadoPedido.ENTREGADO, TipoPedido.MESA));
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 10L, EstadoPedido.CANCELADO));

        dadoPedido(pedido(EstadoPedido.CANCELADO, TipoPedido.MESA));
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 10L, EstadoPedido.PENDIENTE));
    }

    @Test
    void pedidoDeOtroRestauranteNoSeEncuentra() {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(1L, 99L))
                .thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 99L, EstadoPedido.EN_PREPARACION));
        assertEquals("Pedido no encontrado", ex.getMessage());
    }
}
