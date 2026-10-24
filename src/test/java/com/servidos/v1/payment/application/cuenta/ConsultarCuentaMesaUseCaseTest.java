package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarCuentaMesaUseCaseTest {

    @Mock MesaJpaRepository mesaRepository;
    @Mock PedidoJpaRepository pedidoRepository;
    @Mock DetallePedidoJpaRepository detalleRepository;

    @InjectMocks ConsultarCuentaMesaUseCase useCase;

    private MesaJpaEntity mesa() {
        var m = new MesaJpaEntity();
        m.setMesaId(7L);
        m.setRestauranteId(1L);
        m.setNumero(5);
        m.setCapacidad(4);
        m.setComensales(3);
        m.setEstado(EstadoMesa.OCUPADA);
        return m;
    }

    private PedidoJpaEntity pedido(Long id, TipoPedido tipo, String total) {
        var p = new PedidoJpaEntity();
        p.setPedidoId(id);
        p.setRestauranteId(1L);
        p.setMesaId(7L);
        p.setTipoPedido(tipo);
        p.setEstado(EstadoPedido.ENTREGADO);
        p.setTotal(new BigDecimal(total));
        p.setObservacion("sin ají");
        p.setCreatedAt(LocalDateTime.of(2026, 10, 24, 12, 0));
        return p;
    }

    private DetallePedidoJpaEntity detalle(Long pedidoId, String nombre, int cantidad, String subtotal) {
        var d = new DetallePedidoJpaEntity();
        d.setPedidoId(pedidoId);
        d.setRestauranteId(1L);
        d.setNombreProducto(nombre);
        d.setCantidad(cantidad);
        d.setSubtotal(new BigDecimal(subtotal));
        return d;
    }

    @Test
    void agrupaLosItemsPorPedidoYSumaElTotal() {
        when(mesaRepository.findByMesaIdAndRestauranteId(7L, 1L)).thenReturn(Optional.of(mesa()));
        when(pedidoRepository.buscarPendientesDeCobroEnMesa(7L, 1L)).thenReturn(List.of(
                pedido(10L, TipoPedido.MESA, "50.00"), pedido(11L, TipoPedido.RECOJO, "25.50")));
        when(detalleRepository.findByRestauranteIdAndPedidoIdInOrderByDetalleIdAsc(any(), any())).thenReturn(List.of(
                detalle(10L, "Lomo saltado", 2, "50.00"), detalle(11L, "Ceviche", 1, "25.50")));

        var cuenta = useCase.ejecutar(7L, 1L);

        assertEquals(7L, cuenta.mesaId());
        assertEquals(5, cuenta.numero());
        assertEquals(EstadoMesa.OCUPADA, cuenta.estado());
        assertEquals(3, cuenta.comensales());
        assertEquals(4, cuenta.capacidad());
        assertEquals(2, cuenta.pedidos().size());
        assertEquals(0, new BigDecimal("75.50").compareTo(cuenta.total()));
        var primero = cuenta.pedidos().get(0);
        assertEquals(10L, primero.pedidoId());
        assertEquals(TipoPedido.MESA, primero.tipoPedido());
        assertEquals("sin ají", primero.observacion());
        assertEquals(1, primero.items().size());
        assertEquals("Lomo saltado", primero.items().get(0).nombre());
        assertEquals(2, primero.items().get(0).cantidad());
        assertEquals("Ceviche", cuenta.pedidos().get(1).items().get(0).nombre());
    }

    @Test
    void sinPendientesDevuelveCuentaVaciaYNoBuscaItems() {
        when(mesaRepository.findByMesaIdAndRestauranteId(7L, 1L)).thenReturn(Optional.of(mesa()));
        when(pedidoRepository.buscarPendientesDeCobroEnMesa(7L, 1L)).thenReturn(List.of());

        var cuenta = useCase.ejecutar(7L, 1L);

        assertTrue(cuenta.pedidos().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(cuenta.total()));
        verify(detalleRepository, never()).findByRestauranteIdAndPedidoIdInOrderByDetalleIdAsc(any(), any());
    }

    @Test
    void mesaInexistenteODeOtroTenantSeRechaza() {
        when(mesaRepository.findByMesaIdAndRestauranteId(7L, 99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(7L, 99L));

        assertEquals("Mesa no encontrada", ex.getMessage());
        verify(pedidoRepository, never()).buscarPendientesDeCobroEnMesa(any(), any());
    }

    @Test
    void exigeIdentificacion() {
        assertEquals("Restaurante no identificado",
                assertThrows(BusinessException.class, () -> useCase.ejecutar(7L, null)).getMessage());
        assertEquals("Mesa no identificada",
                assertThrows(BusinessException.class, () -> useCase.ejecutar(null, 1L)).getMessage());
    }
}
