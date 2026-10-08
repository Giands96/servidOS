package com.servidos.v1.payment;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.application.pago.ReembolsarPagoCommand;
import com.servidos.v1.payment.application.pago.RegistrarReembolsoUseCase;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * De punta a punta contra Postgres: el reembolso publica el evento, el listener de
 * ordering corre en la misma transacción y tiene que ver el pago ya REEMBOLSADO
 * (auto-flush) para poder cancelar el pedido. Rollback al terminar cada test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReembolsoCancelaPedidoIntegracionTest {

    @Autowired RegistrarReembolsoUseCase reembolso;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired PedidoJpaRepository pedidoRepository;
    @Autowired PagoJpaRepository pagoRepository;

    Long restauranteId;
    Long usuarioId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-reembolso-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();

        var u = new UsuarioJpaEntity();
        u.setNombre("Caja");
        u.setApellido("IT");
        u.setEmail("it-reembolso-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        CurrentUser.setCurrentUser(usuarioId);
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private Long pedidoPagado(EstadoPedido estado) {
        var p = new PedidoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setUsuarioId(usuarioId);
        p.setTipoPedido(TipoPedido.RECOJO);
        p.setEstado(estado);
        p.setTotal(new BigDecimal("50.00"));
        Long pedidoId = pedidoRepository.saveAndFlush(p).getPedidoId();

        var pago = new PagoJpaEntity();
        pago.setPedidoId(pedidoId);
        pago.setRestauranteId(restauranteId);
        pago.setUsuarioId(usuarioId);
        pago.setMetodoPago(MetodoPago.YAPE);
        pago.setMonto(new BigDecimal("50.00"));
        pago.setEstado(EstadoPago.PAGADO);
        pago.setFechaPago(LocalDateTime.now());
        return pagoRepository.saveAndFlush(pago).getPagoId();
    }

    private EstadoPedido estadoDelPedidoDe(Long pagoId) {
        var pago = pagoRepository.findById(pagoId).orElseThrow();
        return pedidoRepository.findById(pago.getPedidoId()).orElseThrow().getEstado();
    }

    @Test
    void reembolsoDePedidoEnPreparacionLoCancela() {
        Long pagoId = pedidoPagado(EstadoPedido.EN_PREPARACION);

        reembolso.ejecutar(new ReembolsarPagoCommand(pagoId, "Cliente se fue"), restauranteId);

        assertEquals(EstadoPago.REEMBOLSADO, pagoRepository.findById(pagoId).orElseThrow().getEstado());
        assertEquals(EstadoPedido.CANCELADO, estadoDelPedidoDe(pagoId));
    }

    @Test
    void reembolsoDePedidoEntregadoNoLoToca() {
        Long pagoId = pedidoPagado(EstadoPedido.ENTREGADO);

        reembolso.ejecutar(new ReembolsarPagoCommand(pagoId, "Producto en mal estado"), restauranteId);

        assertEquals(EstadoPedido.ENTREGADO, estadoDelPedidoDe(pagoId));
    }
}
