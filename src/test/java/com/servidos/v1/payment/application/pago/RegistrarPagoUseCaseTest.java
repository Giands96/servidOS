package com.servidos.v1.payment.application;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.application.pago.RegistrarPagoCommand;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.payment.infrastructure.mapper.PagoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarPagoUseCaseTest {

    @Mock PedidoJpaRepository pedidoRepository;
    @Mock PagoJpaRepository pagoRepository;
    @Mock PagoMapper pagoMapper;
    @Mock EventPublisher eventPublisher;

    @InjectMocks RegistrarPagoUseCase useCase;

    @org.junit.jupiter.api.BeforeEach
    void sinReembolsoPrevio() {
        org.mockito.Mockito.lenient()
                .when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.REEMBOLSADO))
                .thenReturn(false);
    }

    private PedidoJpaEntity pedido(EstadoPedido estado, String total) {
        var p = new PedidoJpaEntity();
        p.setPedidoId(10L);
        p.setRestauranteId(1L);
        p.setEstado(estado);
        p.setTipoPedido(TipoPedido.MESA);
        p.setTotal(new BigDecimal(total));
        return p;
    }

    private void stubPedido(EstadoPedido estado) {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(10L, 1L))
                .thenReturn(Optional.of(pedido(estado, "50.00")));
    }

    /**
     * El hueco que se cerro: un pedido CANCELADO se podia cobrar igual y devolvia 201.
     * El estado PAGADO no existe en EstadoPedido, asi que la unica guarda por estado
     * del pedido es CANCELADO.
     */
    @Test
    void pedidoCanceladoNoSePuedePagar() {
        stubPedido(EstadoPedido.CANCELADO);

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), 1L, 5L));

        assertEquals("El pedido está cancelado, no se puede registrar el pago", ex.getMessage());
        verify(pagoRepository, never()).saveAndFlush(any());
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void pedidoYaPagadoNoSeCobraDeNuevo() {
        stubPedido(EstadoPedido.LISTO);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.PAGADO))
                .thenReturn(true);

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), 1L, 5L));

        assertEquals("Pedido ya pagado", ex.getMessage());
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    /** El reembolso es definitivo: no se vuelve a cobrar, y el mensaje lo dice. */
    @Test
    void pedidoReembolsadoNoSeVuelveACobrar() {
        stubPedido(EstadoPedido.ENTREGADO);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.PAGADO))
                .thenReturn(false);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.REEMBOLSADO))
                .thenReturn(true);

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), 1L, 5L));

        assertEquals("El pedido fue reembolsado, no se puede volver a cobrar", ex.getMessage());
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    /**
     * Anti-tamper: el monto se toma de {@code pedido.total}, nunca del request. Un cajero
     * que mande 1.00 para un pedido de 50 no puede.
     */
    @Test
    void elMontoVieneDelPedidoNoDelRequest() {
        stubPedido(EstadoPedido.ENTREGADO);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.PAGADO))
                .thenReturn(false);
        when(pagoMapper.toEntity(any())).thenCallRealMethod();
        when(pagoMapper.toDomain(any())).thenCallRealMethod();
        when(pagoRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE,
                new BigDecimal("1.00"), null), 1L, 5L);

        var captor = ArgumentCaptor.forClass(com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity.class);
        verify(pagoRepository).saveAndFlush(captor.capture());
        assertEquals(0, new BigDecimal("50.00").compareTo(captor.getValue().getMonto()));
    }

    @Test
    void efectivoInsuficienteFalla() {
        stubPedido(EstadoPedido.ENTREGADO);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.PAGADO))
                .thenReturn(false);

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.EFECTIVO,
                        new BigDecimal("10.00"), null), 1L, 5L));

        assertEquals("Monto insuficiente", ex.getMessage());
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    /** Pagar un PENDIENTE queda permitido: es una decision de negocio abierta. */
    @Test
    void pendienteSePuedePagarPorAhora() {
        stubPedido(EstadoPedido.PENDIENTE);
        when(pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(10L, 1L, EstadoPago.PAGADO))
                .thenReturn(false);
        when(pagoMapper.toEntity(any())).thenCallRealMethod();
        when(pagoMapper.toDomain(any())).thenCallRealMethod();
        when(pagoRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var pago = useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), 1L, 5L);

        assertEquals(EstadoPago.PAGADO, pago.getEstado());
    }

    @Test
    void crossTenantNoEncuentraElPedido() {
        when(pedidoRepository.findByPedidoIdAndRestauranteId(eq(10L), eq(99L)))
                .thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () ->
                useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), 99L, 5L));

        assertEquals("Pedido no encontrado", ex.getMessage());
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    @Test
    void exigeIdentificacion() {
        assertEquals("Restaurante no identificado", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), null, 5L))
                .getMessage());
        assertEquals("Usuario no identificado", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new RegistrarPagoCommand(10L, MetodoPago.YAPE, null, null), 1L, null))
                .getMessage());
    }
}