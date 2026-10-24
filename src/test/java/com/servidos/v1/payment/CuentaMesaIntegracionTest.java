package com.servidos.v1.payment;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoCommand;
import com.servidos.v1.ordering.application.pedido.CrearPedidoItem;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.payment.application.cuenta.ConsultarCuentaMesaUseCase;
import com.servidos.v1.payment.application.cuenta.PagarCuentaMesaCommand;
import com.servidos.v1.payment.application.cuenta.PagarCuentaMesaUseCase;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.infrastructure.jpa.CuentaMesaJpaRepository;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.payment.application.pago.RegistrarPagoCommand;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.shared.exception.BusinessException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La cuenta de una mesa contra Postgres real: la consulta de pendientes de cobro, el lock de la
 * mesa, el cobro conjunto y la liberación automática por los eventos reales. Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CuentaMesaIntegracionTest {

    @Autowired ConsultarCuentaMesaUseCase consultarCuenta;
    @Autowired PagarCuentaMesaUseCase pagarCuenta;
    @Autowired MesaJpaRepository mesaRepository;
    @Autowired PagoJpaRepository pagoRepository;
    @Autowired CuentaMesaJpaRepository cuentaRepository;
    @Autowired CrearMesaUseCase crearMesa;
    @Autowired CrearPedidoUseCase crearPedido;
    @Autowired CambiarEstadoPedidoUseCase cambiarEstado;
    @Autowired RegistrarPagoUseCase registrarPago;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired ProductoJpaRepository productoRepository;

    Long restauranteId;
    Long otroRestauranteId;
    Long usuarioId;
    Long productoId;
    Long mesaId;

    @BeforeEach
    void setUp() {
        restauranteId = nuevoRestaurante("it-cuenta-a-");
        otroRestauranteId = nuevoRestaurante("it-cuenta-b-");

        var u = new UsuarioJpaEntity();
        u.setNombre("Caja");
        u.setApellido("IT");
        u.setEmail("it-cuenta-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        var p = new ProductoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setNombre("Lomo saltado");
        p.setPrecio(new BigDecimal("25.00"));
        p.setEstado(EstadoProducto.DISPONIBLE);
        productoId = productoRepository.save(p).getProductoId();

        mesaId = crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId).getMesa_id();

        CurrentUser.setCurrentUser(usuarioId);
        CurrentUser.setRole("CAJERO");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private Long nuevoRestaurante(String prefijo) {
        var r = new RestauranteJpaEntity();
        r.setSlug(prefijo + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        return restauranteRepository.save(r).getRestauranteId();
    }

    /** Pedido de {@code cantidad} platos de 25.00 (total = cantidad * 25). */
    private Long pedido(TipoPedido tipo, int cantidad) {
        var cmd = new CrearPedidoCommand(tipo, mesaId, "extra salsa", null,
                List.of(new CrearPedidoItem(productoId, cantidad, null)));
        return crearPedido.ejecutar(cmd, restauranteId, usuarioId).getPedido_id();
    }

    @Test
    void laCuentaTraeLasRondasDeMesaYElRecojoVinculadoConSuTotal() {
        Long ronda1 = pedido(TipoPedido.MESA, 2);
        Long ronda2 = pedido(TipoPedido.MESA, 1);
        Long recojo = pedido(TipoPedido.RECOJO, 1);

        var cuenta = consultarCuenta.ejecutar(mesaId, restauranteId);

        assertEquals(mesaId, cuenta.mesaId());
        assertEquals(1, cuenta.numero());
        assertEquals(4, cuenta.capacidad());
        assertEquals(List.of(ronda1, ronda2, recojo),
                cuenta.pedidos().stream().map(p -> p.pedidoId()).toList());
        assertEquals(List.of(TipoPedido.MESA, TipoPedido.MESA, TipoPedido.RECOJO),
                cuenta.pedidos().stream().map(p -> p.tipoPedido()).toList());
        assertEquals(0, new BigDecimal("100.00").compareTo(cuenta.total()));
        var primera = cuenta.pedidos().get(0);
        assertEquals(0, new BigDecimal("50.00").compareTo(primera.total()));
        assertEquals("extra salsa", primera.observacion());
        assertEquals(1, primera.items().size());
        assertEquals("Lomo saltado", primera.items().get(0).nombre());
        assertEquals(2, primera.items().get(0).cantidad());
        assertEquals(0, new BigDecimal("50.00").compareTo(primera.items().get(0).subtotal()));
    }

    @Test
    void unPedidoPagadoPorLaViaIndividualYUnoCanceladoNoAparecen() {
        Long pagado = pedido(TipoPedido.MESA, 1);
        Long cancelado = pedido(TipoPedido.RECOJO, 1);
        Long pendiente = pedido(TipoPedido.MESA, 3);
        registrarPago.ejecutar(new RegistrarPagoCommand(pagado, MetodoPago.YAPE, null, "ref"),
                restauranteId, usuarioId);
        cambiarEstado.ejecutar(cancelado, restauranteId, EstadoPedido.CANCELADO);

        var cuenta = consultarCuenta.ejecutar(mesaId, restauranteId);

        assertEquals(List.of(pendiente), cuenta.pedidos().stream().map(p -> p.pedidoId()).toList());
        assertEquals(0, new BigDecimal("75.00").compareTo(cuenta.total()));
    }

    @Test
    void sinPedidosPendientesLaCuentaVieneVacia() {
        var cuenta = consultarCuenta.ejecutar(mesaId, restauranteId);

        assertTrue(cuenta.pedidos().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(cuenta.total()));
    }

    @Test
    void laMesaDeOtroRestauranteOInexistenteSeRechaza() {
        pedido(TipoPedido.MESA, 1);

        var ajena = assertThrows(BusinessException.class, () -> consultarCuenta.ejecutar(mesaId, otroRestauranteId));
        var inexistente = assertThrows(BusinessException.class,
                () -> consultarCuenta.ejecutar(mesaId + 100_000, restauranteId));

        assertEquals("Mesa no encontrada", ajena.getMessage());
        assertEquals("Mesa no encontrada", inexistente.getMessage());
    }

    // --- Cobro conjunto -------------------------------------------------------------------

    /** PENDIENTE -> EN_PREPARACION -> LISTO -> ENTREGADO, la ruta de un pedido de mesa. */
    private void entregar(Long pedidoId) {
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.EN_PREPARACION);
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.LISTO);
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.ENTREGADO);
    }

    private PagarCuentaMesaCommand pagar(MetodoPago metodo, String entregado, Long... pedidoIds) {
        return new PagarCuentaMesaCommand(mesaId, metodo, entregado == null ? null : new BigDecimal(entregado),
                "ref-cuenta", List.of(pedidoIds));
    }

    private EstadoMesa estadoMesa() {
        return mesaRepository.findById(mesaId).orElseThrow().getEstado();
    }

    private long pagosDe(Long... pedidoIds) {
        return List.of(pedidoIds).stream()
                .filter(id -> pagoRepository.findByPedidoIdAndRestauranteId(id, restauranteId).isPresent())
                .count();
    }

    @Test
    void cobrarLaCuentaCreaUnPagoPorPedidoConElMismoCuentaIdYLiberaLaMesa() {
        Long ronda1 = pedido(TipoPedido.MESA, 2);
        Long ronda2 = pedido(TipoPedido.MESA, 1);
        Long recojo = pedido(TipoPedido.RECOJO, 1);
        entregar(ronda1);
        entregar(ronda2);

        var cuenta = pagarCuenta.ejecutar(pagar(MetodoPago.EFECTIVO, "120.00", ronda1, ronda2, recojo),
                restauranteId, usuarioId);

        assertEquals(0, new BigDecimal("100.00").compareTo(cuenta.total()));
        assertEquals(0, new BigDecimal("20.00").compareTo(cuenta.vuelto()));
        assertEquals(3, cuenta.pagos().size());
        for (Long pedidoId : List.of(ronda1, ronda2, recojo)) {
            var pago = pagoRepository.findByPedidoIdAndRestauranteId(pedidoId, restauranteId).orElseThrow();
            assertEquals(EstadoPago.PAGADO, pago.getEstado());
            assertEquals(cuenta.cuentaId(), pago.getCuentaId());
            assertEquals(MetodoPago.EFECTIVO, pago.getMetodoPago());
            assertEquals("ref-cuenta", pago.getReferenciaExterna());
            assertNull(pago.getVuelto());
        }
        var fila = cuentaRepository.findById(cuenta.cuentaId()).orElseThrow();
        assertEquals(mesaId, fila.getMesaId());
        assertEquals(usuarioId, fila.getUsuarioId());
        assertEquals(0, new BigDecimal("100.00").compareTo(fila.getTotal()));
        assertEquals(0, new BigDecimal("120.00").compareTo(fila.getMontoEntregado()));
        assertEquals(0, new BigDecimal("20.00").compareTo(fila.getVuelto()));
        // Las rondas entregadas y el recojo cobrado (sin entregar) ya no retienen la mesa.
        assertEquals(EstadoMesa.LIBRE, estadoMesa());
        assertTrue(consultarCuenta.ejecutar(mesaId, restauranteId).pedidos().isEmpty());
    }

    @Test
    void cobrarAntesDeEntregarDejaLaMesaOcupadaHastaQueSeEntregue() {
        Long ronda = pedido(TipoPedido.MESA, 1);

        pagarCuenta.ejecutar(pagar(MetodoPago.YAPE, null, ronda), restauranteId, usuarioId);

        assertEquals(EstadoMesa.OCUPADA, estadoMesa());
        entregar(ronda);
        assertEquals(EstadoMesa.LIBRE, estadoMesa());
    }

    @Test
    void unaRondaNuevaEntreLaConsultaYElCobroRechazaElCobroSinCrearPagos() {
        Long ronda = pedido(TipoPedido.MESA, 1);
        var vista = consultarCuenta.ejecutar(mesaId, restauranteId);
        Long rondaNueva = pedido(TipoPedido.MESA, 1);
        long cuentasAntes = cuentaRepository.count();

        var ex = assertThrows(BusinessException.class, () -> pagarCuenta.ejecutar(
                pagar(MetodoPago.YAPE, null, vista.pedidos().get(0).pedidoId()), restauranteId, usuarioId));

        assertEquals("La cuenta cambió, vuelva a cargarla", ex.getMessage());
        assertEquals(0, pagosDe(ronda, rondaNueva));
        assertEquals(cuentasAntes, cuentaRepository.count());
    }

    @Test
    void efectivoInsuficienteNoCreaCuentaNiPagos() {
        Long a = pedido(TipoPedido.MESA, 1);
        Long b = pedido(TipoPedido.RECOJO, 1);
        long cuentasAntes = cuentaRepository.count();

        var ex = assertThrows(BusinessException.class,
                () -> pagarCuenta.ejecutar(pagar(MetodoPago.EFECTIVO, "49.99", a, b), restauranteId, usuarioId));

        assertEquals("Monto insuficiente", ex.getMessage());
        assertEquals(0, pagosDe(a, b));
        assertEquals(cuentasAntes, cuentaRepository.count());
    }

    @Test
    void unPedidoPagadoPorLaViaIndividualYaNoPerteneceALaCuenta() {
        Long a = pedido(TipoPedido.MESA, 1);
        Long b = pedido(TipoPedido.MESA, 1);
        registrarPago.ejecutar(new RegistrarPagoCommand(a, MetodoPago.YAPE, null, null), restauranteId, usuarioId);

        // El cajero cargó la cuenta antes del pago individual y manda ambos pedidos.
        var viejo = assertThrows(BusinessException.class,
                () -> pagarCuenta.ejecutar(pagar(MetodoPago.YAPE, null, a, b), restauranteId, usuarioId));
        assertEquals("La cuenta cambió, vuelva a cargarla", viejo.getMessage());
        assertFalse(pagoRepository.findByPedidoIdAndRestauranteId(b, restauranteId).isPresent());

        // Con la cuenta recargada (solo b) el cobro sale.
        var cuenta = pagarCuenta.ejecutar(pagar(MetodoPago.YAPE, null, b), restauranteId, usuarioId);
        assertEquals(1, cuenta.pagos().size());
    }

    @Test
    void cobrarLaMesaDeOtroRestauranteSeRechazaSinTocarNada() {
        Long a = pedido(TipoPedido.MESA, 1);

        var ex = assertThrows(BusinessException.class,
                () -> pagarCuenta.ejecutar(pagar(MetodoPago.YAPE, null, a), otroRestauranteId, usuarioId));

        assertEquals("Mesa no encontrada", ex.getMessage());
        assertEquals(0, pagosDe(a));
    }

    @Test
    void cobrarUnaCuentaVaciaOYaCobradaFalla() {
        Long a = pedido(TipoPedido.MESA, 1);
        pagarCuenta.ejecutar(pagar(MetodoPago.YAPE, null, a), restauranteId, usuarioId);

        var ex = assertThrows(BusinessException.class,
                () -> pagarCuenta.ejecutar(pagar(MetodoPago.YAPE, null, a), restauranteId, usuarioId));

        assertEquals("La mesa no tiene pedidos pendientes de cobro", ex.getMessage());
    }
}
