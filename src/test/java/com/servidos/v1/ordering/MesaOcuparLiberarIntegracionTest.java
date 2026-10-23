package com.servidos.v1.ordering;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.LiberarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.OcuparMesaCommand;
import com.servidos.v1.ordering.application.mesa.OcuparMesaUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ocupar / liberar y la consulta de "pedidos abiertos" contra Postgres real: el SELECT ... FOR UPDATE
 * de la mesa y el cruce pedido/pago solo se ejercitan con la base de verdad. Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MesaOcuparLiberarIntegracionTest {

    @Autowired CrearMesaUseCase crearMesa;
    @Autowired OcuparMesaUseCase ocuparMesa;
    @Autowired LiberarMesaUseCase liberarMesa;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired PedidoJpaRepository pedidoRepository;
    @Autowired PagoJpaRepository pagoRepository;

    Long restauranteId;
    Long otroRestauranteId;
    Long usuarioId;
    Long mesaId;

    @BeforeEach
    void setUp() {
        restauranteId = nuevoRestaurante("it-ocupar-a-");
        otroRestauranteId = nuevoRestaurante("it-ocupar-b-");

        var u = new UsuarioJpaEntity();
        u.setNombre("Mesero");
        u.setApellido("IT");
        u.setEmail("it-ocupar-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        mesaId = crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId).getMesa_id();
    }

    private Long nuevoRestaurante(String prefijo) {
        var r = new RestauranteJpaEntity();
        r.setSlug(prefijo + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        return restauranteRepository.save(r).getRestauranteId();
    }

    private Long pedidoEnMesa(EstadoPedido estado, EstadoPago pago) {
        return pedidoEnMesa(TipoPedido.MESA, estado, pago);
    }

    private Long pedidoEnMesa(TipoPedido tipo, EstadoPedido estado, EstadoPago pago) {
        var p = new PedidoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setUsuarioId(usuarioId);
        p.setMesaId(mesaId);
        p.setTipoPedido(tipo);
        p.setEstado(estado);
        p.setTotal(new BigDecimal("50.00"));
        Long pedidoId = pedidoRepository.saveAndFlush(p).getPedidoId();
        if (pago != null) {
            var pg = new PagoJpaEntity();
            pg.setPedidoId(pedidoId);
            pg.setRestauranteId(restauranteId);
            pg.setUsuarioId(usuarioId);
            pg.setMetodoPago(MetodoPago.YAPE);
            pg.setMonto(new BigDecimal("50.00"));
            pg.setEstado(pago);
            pg.setFechaPago(LocalDateTime.now());
            pagoRepository.saveAndFlush(pg);
        }
        return pedidoId;
    }

    @Test
    void ocuparYLiberarUnaMesaSinPedidos() {
        var ocupada = ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 3), restauranteId);
        assertEquals(EstadoMesa.OCUPADA, ocupada.getEstado());
        assertEquals(3, ocupada.getComensales());

        var libre = liberarMesa.ejecutar(mesaId, restauranteId);
        assertEquals(EstadoMesa.LIBRE, libre.getEstado());
        assertNull(libre.getComensales());
    }

    @Test
    void ocuparDosVecesFalla() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 3), restauranteId);

        assertThrows(BusinessException.class,
                () -> ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId));
    }

    @Test
    void laMesaDeOtroRestauranteNoSePuedeOcuparNiLiberar() {
        assertThrows(BusinessException.class,
                () -> ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 3), otroRestauranteId));
        assertThrows(BusinessException.class, () -> liberarMesa.ejecutar(mesaId, otroRestauranteId));
    }

    @Test
    void pedidoEnCursoOEntregadoSinCobrarMantienenLaMesaAbierta() {
        pedidoEnMesa(EstadoPedido.EN_PREPARACION, null);
        pedidoEnMesa(EstadoPedido.ENTREGADO, null);
        // Cobrado pero todavía no entregado: el cliente sigue esperando su comida.
        pedidoEnMesa(EstadoPedido.LISTO, EstadoPago.PAGADO);

        assertEquals(3L, pedidoRepository.contarAbiertosEnMesa(mesaId, restauranteId));
    }

    @Test
    void cuentaComoCerradoElCanceladoYElEntregadoYaCobradoOReembolsado() {
        pedidoEnMesa(EstadoPedido.CANCELADO, null);
        pedidoEnMesa(EstadoPedido.ENTREGADO, EstadoPago.PAGADO);
        pedidoEnMesa(EstadoPedido.ENTREGADO, EstadoPago.REEMBOLSADO);

        assertEquals(0L, pedidoRepository.contarAbiertosEnMesa(mesaId, restauranteId));
    }

    @Test
    void laConsultaFiltraPorRestaurante() {
        pedidoEnMesa(EstadoPedido.PENDIENTE, null);

        assertEquals(1L, pedidoRepository.contarAbiertosEnMesa(mesaId, restauranteId));
        assertEquals(0L, pedidoRepository.contarAbiertosEnMesa(mesaId, otroRestauranteId));
    }

    @Test
    void losPedidosDeliveryYRecojoConMesaNoRetienenLaMesa() {
        pedidoEnMesa(TipoPedido.DELIVERY, EstadoPedido.EN_ENTREGA, null);
        pedidoEnMesa(TipoPedido.RECOJO, EstadoPedido.PENDIENTE, null);

        assertEquals(0L, pedidoRepository.contarAbiertosEnMesa(mesaId, restauranteId));
    }

    @Test
    void liberarManualIgnoraPedidosNoMesaAbiertos() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId);
        pedidoEnMesa(TipoPedido.DELIVERY, EstadoPedido.PENDIENTE, null);

        assertEquals(EstadoMesa.LIBRE, liberarMesa.ejecutar(mesaId, restauranteId).getEstado());
    }

    @Test
    void liberarManualSoloSinPedidosAbiertos() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId);
        Long pedidoId = pedidoEnMesa(EstadoPedido.PENDIENTE, null);

        var ex = assertThrows(BusinessException.class, () -> liberarMesa.ejecutar(mesaId, restauranteId));
        assertEquals("La mesa tiene pedidos abiertos", ex.getMessage());

        var pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        pedido.setEstado(EstadoPedido.CANCELADO);
        pedidoRepository.saveAndFlush(pedido);

        assertEquals(EstadoMesa.LIBRE, liberarMesa.ejecutar(mesaId, restauranteId).getEstado());
    }
}
