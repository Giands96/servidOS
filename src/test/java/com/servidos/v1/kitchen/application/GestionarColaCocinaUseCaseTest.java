package com.servidos.v1.kitchen.application;

import com.servidos.v1.kitchen.application.cocina.GestionarColaCocinaUseCase;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.DetallePedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre {@link GestionarColaCocinaUseCase}, que era el unico use case del proyecto sin
 * ningun test. El foco es que cocina no pueda dejar un pedido en un estado invalido:
 * {@code marcarListo} escribe directo en {@code ordering.pedido} sin pasar por
 * {@code CambiarEstadoPedidoUseCase}, asi que la unica barrera es la validacion propia
 * del metodo. Estos tests son los que la sostienen.
 */
@ExtendWith(MockitoExtension.class)
class GestionarColaCocinaUseCaseTest {

    @Mock PedidoJpaRepository pedidoRepository;
    @Mock com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository detalleRepository;
    @Mock DetallePedidoMapper detallePedidoMapper;
    @Mock EventPublisher eventPublisher;

    @InjectMocks GestionarColaCocinaUseCase useCase;

    private PedidoJpaEntity pedido(EstadoPedido estado) {
        var p = new PedidoJpaEntity();
        p.setPedidoId(10L);
        p.setRestauranteId(1L);
        p.setEstado(estado);
        p.setTipoPedido(TipoPedido.MESA);
        return p;
    }

    private void stubPedido(EstadoPedido estado) {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(10L, 1L))
                .thenReturn(Optional.of(pedido(estado)));
    }

    @Test
    void marcarListoMueveDeEnPreparacionAListo() {
        stubPedido(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        useCase.marcarListo(10L, 1L);

        var captor = ArgumentCaptor.forClass(PedidoJpaEntity.class);
        verify(pedidoRepository).save(captor.capture());
        assertEquals(EstadoPedido.LISTO, captor.getValue().getEstado());
        verify(eventPublisher).publish(any());
    }

    /**
     * La barrera que importa: cocina no puede llevar un pedido a LISTO si no está en
     * preparación. Sin esto, un pedido PENDIENTE o ya ENTREGADO podría "resucitar" como
     * LISTO saltandose la matriz de estados de ordering.
     */
    @Test
    void marcarListoSobrePedidoNoEnPreparacionFalla() {
        for (var estado : List.of(EstadoPedido.PENDIENTE, EstadoPedido.LISTO,
                EstadoPedido.EN_ENTREGA, EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO)) {
            stubPedido(estado);

            var ex = assertThrows(BusinessException.class, () -> useCase.marcarListo(10L, 1L),
                    "un pedido en " + estado + " no debería poder marcarse como LISTO");
            assertEquals("Pedido no está en preparación", ex.getMessage());

            verify(pedidoRepository, never()).save(any());
        }
    }

    /** Cross-tenant: cocina de un restaurante no toca el pedido de otro. */
    @Test
    void marcarListoDePedidoAjenoFalla() {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(99L, 1L))
                .thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.marcarListo(99L, 1L));

        assertEquals("Pedido no encontrado", ex.getMessage());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void marcarListoExigeIdentificacion() {
        assertEquals("Pedido no identificado",
                assertThrows(BusinessException.class, () -> useCase.marcarListo(null, 1L)).getMessage());
        assertEquals("Restaurante no identificado",
                assertThrows(BusinessException.class, () -> useCase.marcarListo(10L, null)).getMessage());
    }

    @Test
    void laColaSoloTraePedidosEnPreparacionDelTenant() {
        when(pedidoRepository.findByRestauranteIdAndEstado(1L, EstadoPedido.EN_PREPARACION))
                .thenReturn(List.of(pedido(EstadoPedido.EN_PREPARACION)));
        when(detalleRepository.findByPedidoIdAndRestauranteId(10L, 1L)).thenReturn(List.of());

        var cola = useCase.listarEnPreparacion(1L);

        assertEquals(1, cola.size());
        assertEquals(EstadoPedido.EN_PREPARACION, cola.get(0).getEstado());
        verify(pedidoRepository).findByRestauranteIdAndEstado(eq(1L), eq(EstadoPedido.EN_PREPARACION));
    }

    @Test
    void laColaExigeRestaurante() {
        var ex = assertThrows(BusinessException.class, () -> useCase.listarEnPreparacion(null));
        assertEquals("Restaurante no identificado", ex.getMessage());
        verify(pedidoRepository, never()).findByRestauranteIdAndEstado(any(), any());
    }

    @Test
    void colaVaciaDevuelveListaVacia() {
        when(pedidoRepository.findByRestauranteIdAndEstado(1L, EstadoPedido.EN_PREPARACION))
                .thenReturn(List.of());

        assertTrue(useCase.listarEnPreparacion(1L).isEmpty());
    }
}