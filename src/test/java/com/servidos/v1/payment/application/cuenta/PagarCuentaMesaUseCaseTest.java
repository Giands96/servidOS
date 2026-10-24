package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.domain.event.PagoRegistradoEvent;
import com.servidos.v1.payment.infrastructure.jpa.CuentaMesaJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.CuentaMesaJpaRepository;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.payment.infrastructure.mapper.PagoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagarCuentaMesaUseCaseTest {

    static final String CUENTA_CAMBIO = "La cuenta cambió, vuelva a cargarla";

    @Mock MesaJpaRepository mesaRepository;
    @Mock PedidoJpaRepository pedidoRepository;
    @Mock PagoJpaRepository pagoRepository;
    @Mock CuentaMesaJpaRepository cuentaRepository;
    @Spy PagoMapper pagoMapper = new PagoMapper();
    @Mock EventPublisher eventPublisher;

    @InjectMocks PagarCuentaMesaUseCase useCase;

    final AtomicLong pagoIds = new AtomicLong(100);

    @BeforeEach
    void repositoriosQueAsignanId() {
        lenient().when(cuentaRepository.saveAndFlush(any())).thenAnswer(i -> {
            CuentaMesaJpaEntity c = i.getArgument(0);
            c.setCuentaId(55L);
            return c;
        });
        lenient().when(pagoRepository.saveAndFlush(any())).thenAnswer(i -> {
            PagoJpaEntity p = i.getArgument(0);
            p.setPagoId(pagoIds.incrementAndGet());
            return p;
        });
    }

    private MesaJpaEntity mesa() {
        var m = new MesaJpaEntity();
        m.setMesaId(7L);
        m.setRestauranteId(1L);
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
        return p;
    }

    private void cuentaConDosPedidos() {
        when(mesaRepository.bloquearPorIdYRestaurante(7L, 1L)).thenReturn(Optional.of(mesa()));
        when(pedidoRepository.buscarPendientesDeCobroEnMesa(7L, 1L)).thenReturn(List.of(
                pedido(10L, TipoPedido.MESA, "50.00"), pedido(11L, TipoPedido.RECOJO, "25.50")));
    }

    private PagarCuentaMesaCommand cmd(MetodoPago metodo, String entregado, Long... pedidoIds) {
        return new PagarCuentaMesaCommand(7L, metodo, entregado == null ? null : new BigDecimal(entregado),
                "ref-1", List.of(pedidoIds));
    }

    @Test
    void cobraUnPagoPorPedidoYPublicaLosEventosDespuesDeGuardarTodos() {
        cuentaConDosPedidos();

        var cuenta = useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L, 11L), 1L, 5L);

        assertEquals(55L, cuenta.cuentaId());
        assertEquals(7L, cuenta.mesaId());
        assertEquals(0, new BigDecimal("75.50").compareTo(cuenta.total()));
        assertEquals(MetodoPago.YAPE, cuenta.metodoPago());
        assertNull(cuenta.vuelto());
        assertEquals(List.of(10L, 11L), cuenta.pagos().stream().map(p -> p.pedidoId()).toList());
        assertEquals(List.of(101L, 102L), cuenta.pagos().stream().map(p -> p.pagoId()).toList());
        assertEquals(0, new BigDecimal("25.50").compareTo(cuenta.pagos().get(1).monto()));

        var pagos = ArgumentCaptor.forClass(PagoJpaEntity.class);
        verify(pagoRepository, org.mockito.Mockito.times(2)).saveAndFlush(pagos.capture());
        pagos.getAllValues().forEach(p -> {
            assertEquals(55L, p.getCuentaId());
            assertEquals(EstadoPago.PAGADO, p.getEstado());
            assertEquals(MetodoPago.YAPE, p.getMetodoPago());
            assertEquals(5L, p.getUsuarioId());
            assertEquals("ref-1", p.getReferenciaExterna());
            assertNull(p.getVuelto());
        });

        // Los eventos salen recién cuando están guardados todos los pagos de la cuenta.
        InOrder orden = inOrder(pagoRepository, eventPublisher);
        orden.verify(pagoRepository, org.mockito.Mockito.times(2)).saveAndFlush(any());
        var eventos = ArgumentCaptor.forClass(Object.class);
        orden.verify(eventPublisher, org.mockito.Mockito.times(2)).publish(eventos.capture());
        var primero = (PagoRegistradoEvent) eventos.getAllValues().get(0);
        assertEquals(101L, primero.getPagoId());
        assertEquals(10L, primero.getPedidoId());
        assertEquals(1L, primero.getRestauranteId());
    }

    @Test
    void elVueltoDelEfectivoSeCalculaUnaVezSobreElTotalYViveEnLaCuenta() {
        cuentaConDosPedidos();

        var cuenta = useCase.ejecutar(cmd(MetodoPago.EFECTIVO, "100.00", 10L, 11L), 1L, 5L);

        assertEquals(0, new BigDecimal("24.50").compareTo(cuenta.vuelto()));
        var guardada = ArgumentCaptor.forClass(CuentaMesaJpaEntity.class);
        verify(cuentaRepository).saveAndFlush(guardada.capture());
        assertEquals(0, new BigDecimal("75.50").compareTo(guardada.getValue().getTotal()));
        assertEquals(0, new BigDecimal("100.00").compareTo(guardada.getValue().getMontoEntregado()));
        assertEquals(0, new BigDecimal("24.50").compareTo(guardada.getValue().getVuelto()));
        assertEquals(1L, guardada.getValue().getRestauranteId());
        assertEquals(7L, guardada.getValue().getMesaId());
        assertEquals(5L, guardada.getValue().getUsuarioId());
        var pagos = ArgumentCaptor.forClass(PagoJpaEntity.class);
        verify(pagoRepository, org.mockito.Mockito.times(2)).saveAndFlush(pagos.capture());
        pagos.getAllValues().forEach(p -> assertNull(p.getVuelto()));
    }

    @Test
    void efectivoInsuficienteNoCreaNada() {
        cuentaConDosPedidos();

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.EFECTIVO, "75.49", 10L, 11L), 1L, 5L));

        assertEquals("Monto insuficiente", ex.getMessage());
        verify(cuentaRepository, never()).saveAndFlush(any());
        verify(pagoRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void efectivoSinMontoEntregadoFalla() {
        cuentaConDosPedidos();

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.EFECTIVO, null, 10L, 11L), 1L, 5L));

        assertEquals("Monto insuficiente", ex.getMessage());
    }

    @Test
    void unConjuntoDistintoDeLoPendienteSeRechazaSinCrearNada() {
        cuentaConDosPedidos();

        var faltante = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L), 1L, 5L));
        var sobrante = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L, 11L, 12L), 1L, 5L));
        var ajeno = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L, 99L), 1L, 5L));

        assertEquals(CUENTA_CAMBIO, faltante.getMessage());
        assertEquals(CUENTA_CAMBIO, sobrante.getMessage());
        assertEquals(CUENTA_CAMBIO, ajeno.getMessage());
        verify(cuentaRepository, never()).saveAndFlush(any());
        verify(pagoRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void losIdsRepetidosSeTratanComoUnConjunto() {
        cuentaConDosPedidos();

        var cuenta = useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L, 11L, 10L), 1L, 5L);

        assertEquals(2, cuenta.pagos().size());
    }

    @Test
    void conLaCuentaVaciaFallaConUnMensajeClaro() {
        when(mesaRepository.bloquearPorIdYRestaurante(7L, 1L)).thenReturn(Optional.of(mesa()));
        when(pedidoRepository.buscarPendientesDeCobroEnMesa(7L, 1L)).thenReturn(List.of());

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L), 1L, 5L));

        assertEquals("La mesa no tiene pedidos pendientes de cobro", ex.getMessage());
        verify(pagoRepository, never()).saveAndFlush(any());
    }

    @Test
    void mesaInexistenteODeOtroTenantSeRechaza() {
        when(mesaRepository.bloquearPorIdYRestaurante(7L, 99L)).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L), 99L, 5L));

        assertEquals("Mesa no encontrada", ex.getMessage());
        verify(pedidoRepository, never()).buscarPendientesDeCobroEnMesa(any(), any());
    }

    @Test
    void unPagoIndividualConcurrenteRevierteLaCuentaYNoPublicaEventos() {
        cuentaConDosPedidos();
        doAnswer(i -> {
            PagoJpaEntity p = i.getArgument(0);
            p.setPagoId(pagoIds.incrementAndGet());
            return p;
        }).doThrow(violacionDe("pago_pedido_id_key"))
                .when(pagoRepository).saveAndFlush(any());

        var ex = assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L, 11L), 1L, 5L));

        assertEquals(CUENTA_CAMBIO, ex.getMessage());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void otraViolacionDeIntegridadNoSeDisfrazaDeCuentaCambiada() {
        cuentaConDosPedidos();
        var otra = violacionDe("pago_cuenta_id_fkey");
        doThrow(otra).when(pagoRepository).saveAndFlush(any());

        var ex = assertThrows(DataIntegrityViolationException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L, 11L), 1L, 5L));

        assertSame(otra, ex);
        verify(eventPublisher, never()).publish(any());
    }

    /** Como la arma Spring con Postgres: la causa de Hibernate trae el nombre del constraint. */
    private static DataIntegrityViolationException violacionDe(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraint));
    }

    @Test
    void exigeDatosObligatorios() {
        assertEquals("Restaurante no identificado", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L), null, 5L)).getMessage());
        assertEquals("Usuario no identificado", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(MetodoPago.YAPE, null, 10L), 1L, null)).getMessage());
        assertEquals("Mesa no identificada", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new PagarCuentaMesaCommand(null, MetodoPago.YAPE, null, null, List.of(10L)),
                        1L, 5L)).getMessage());
        assertEquals("El método de pago es obligatorio", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(cmd(null, null, 10L), 1L, 5L)).getMessage());
        assertEquals("Debe indicar los pedidos de la cuenta", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new PagarCuentaMesaCommand(7L, MetodoPago.YAPE, null, null, List.of()),
                        1L, 5L)).getMessage());
        assertEquals("Debe indicar los pedidos de la cuenta", assertThrows(BusinessException.class,
                () -> useCase.ejecutar(new PagarCuentaMesaCommand(7L, MetodoPago.YAPE, null, null, null),
                        1L, 5L)).getMessage());
        verify(mesaRepository, never()).bloquearPorIdYRestaurante(any(), any());
    }
}
