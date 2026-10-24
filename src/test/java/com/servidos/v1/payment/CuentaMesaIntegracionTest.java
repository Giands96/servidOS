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
import com.servidos.v1.payment.application.cuenta.ConsultarCuentaMesaUseCase;
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
}
