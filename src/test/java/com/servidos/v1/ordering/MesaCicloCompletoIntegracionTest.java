package com.servidos.v1.ordering;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.LiberarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.OcuparMesaCommand;
import com.servidos.v1.ordering.application.mesa.OcuparMesaUseCase;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoCommand;
import com.servidos.v1.ordering.application.pedido.CrearPedidoItem;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.payment.application.pago.ReembolsarPagoCommand;
import com.servidos.v1.payment.application.pago.RegistrarPagoCommand;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.application.pago.RegistrarReembolsoUseCase;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ciclo completo de una mesa contra Postgres real, por los casos de uso reales y los eventos
 * reales: crear mesa, ocuparla, tomar varios pedidos, entregarlos y cobrarlos. La mesa queda
 * LIBRE recién cuando el último pedido está entregado y cobrado, en cualquier orden. Rollback
 * por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MesaCicloCompletoIntegracionTest {

    @Autowired CrearMesaUseCase crearMesa;
    @Autowired OcuparMesaUseCase ocuparMesa;
    @Autowired LiberarMesaUseCase liberarMesa;
    @Autowired CrearPedidoUseCase crearPedido;
    @Autowired CambiarEstadoPedidoUseCase cambiarEstado;
    @Autowired RegistrarPagoUseCase registrarPago;
    @Autowired RegistrarReembolsoUseCase registrarReembolso;
    @Autowired MesaJpaRepository mesaRepository;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired ProductoJpaRepository productoRepository;

    Long restauranteId;
    Long usuarioId;
    Long productoId;
    Long mesaId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-mesa-ciclo-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();

        var u = new UsuarioJpaEntity();
        u.setNombre("Caja");
        u.setApellido("IT");
        u.setEmail("it-mesa-ciclo-" + System.nanoTime() + "@test.local");
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
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private Long pedido(TipoPedido tipo, Long mesa) {
        var cmd = new CrearPedidoCommand(tipo, mesa, null, tipo == TipoPedido.DELIVERY ? "Juan" : null,
                List.of(new CrearPedidoItem(productoId, 1, null)));
        return crearPedido.ejecutar(cmd, restauranteId, usuarioId).getPedido_id();
    }

    private Long pedidoDeMesa() {
        return pedido(TipoPedido.MESA, mesaId);
    }

    /** PENDIENTE -> EN_PREPARACION -> LISTO -> ENTREGADO, la ruta de un pedido de mesa. */
    private void entregar(Long pedidoId) {
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.EN_PREPARACION);
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.LISTO);
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.ENTREGADO);
    }

    private Long pagar(Long pedidoId) {
        return registrarPago.ejecutar(
                new RegistrarPagoCommand(pedidoId, MetodoPago.YAPE, null, "ref-" + pedidoId),
                restauranteId, usuarioId).getPago_id();
    }

    private MesaJpaEntity mesa() {
        return mesaRepository.findById(mesaId).orElseThrow();
    }

    private void assertOcupada() {
        assertEquals(EstadoMesa.OCUPADA, mesa().getEstado());
    }

    private void assertLibre() {
        var m = mesa();
        assertEquals(EstadoMesa.LIBRE, m.getEstado());
        assertNull(m.getComensales());
    }

    @Test
    void cobrarYEntregarTodosLosPedidosLiberaLaMesa() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 4), restauranteId);
        Long a = pedidoDeMesa();
        Long b = pedidoDeMesa();

        entregar(a);
        pagar(a);
        assertOcupada();

        entregar(b);
        assertOcupada();
        pagar(b);
        assertLibre();
    }

    @Test
    void sirveElOrdenInversoPagarPrimeroYEntregarDespues() {
        Long a = pedidoDeMesa();
        Long b = pedidoDeMesa();

        pagar(a);
        pagar(b);
        assertOcupada();

        entregar(a);
        assertOcupada();
        entregar(b);
        assertLibre();
    }

    @Test
    void conUnPedidoSinTerminarLaMesaSigueOcupada() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId);
        Long a = pedidoDeMesa();
        pedidoDeMesa();

        entregar(a);
        pagar(a);

        assertOcupada();
        assertEquals(2, mesa().getComensales());
    }

    @Test
    void entregadoSinCobrarNoLiberaLaMesa() {
        Long a = pedidoDeMesa();

        entregar(a);

        assertOcupada();
    }

    @Test
    void cobradoSinEntregarNoLiberaLaMesa() {
        Long a = pedidoDeMesa();

        pagar(a);

        assertOcupada();
    }

    @Test
    void unPedidoEntregadoYReembolsadoNoRetieneLaMesa() {
        Long a = pedidoDeMesa();
        Long b = pedidoDeMesa();
        entregar(a);
        Long pagoA = pagar(a);
        registrarReembolso.ejecutar(new ReembolsarPagoCommand(pagoA, "Producto en mal estado"), restauranteId);
        assertOcupada();

        entregar(b);
        pagar(b);

        assertLibre();
    }

    @Test
    void reembolsarUnPedidoSinEntregarLoCancelaYLiberaLaMesa() {
        Long a = pedidoDeMesa();
        Long pagoA = pagar(a);
        assertOcupada();

        registrarReembolso.ejecutar(new ReembolsarPagoCommand(pagoA, "Cliente se fue"), restauranteId);

        assertLibre();
    }

    @Test
    void liberarManualSoloSinPedidosAbiertos() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId);
        Long a = pedidoDeMesa();

        assertThrows(BusinessException.class, () -> liberarMesa.ejecutar(mesaId, restauranteId));

        cambiarEstado.ejecutar(a, restauranteId, EstadoPedido.CANCELADO);
        // La cancelación ya la liberó sola: el manual sobre una mesa libre se rechaza.
        assertLibre();
        assertThrows(BusinessException.class, () -> liberarMesa.ejecutar(mesaId, restauranteId));
    }

    @Test
    void unRecojoVinculadoSinCobrarImpideLaLiberacionAutomatica() {
        Long ronda = pedidoDeMesa();
        Long recojo = pedido(TipoPedido.RECOJO, mesaId);

        entregar(ronda);
        pagar(ronda);

        // La ronda ya está entregada y cobrada, pero el recojo de la mesa sigue sin cobrar.
        assertOcupada();

        // Cobrarlo basta (aunque no esté entregado) para cerrar la mesa.
        pagar(recojo);
        assertLibre();
    }

    @Test
    void unRecojoVinculadoSinCobrarImpideLaLiberacionManual() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId);
        Long recojo = pedido(TipoPedido.RECOJO, mesaId);

        var ex = assertThrows(BusinessException.class, () -> liberarMesa.ejecutar(mesaId, restauranteId));
        assertEquals("La mesa tiene pedidos abiertos", ex.getMessage());
        assertOcupada();

        pagar(recojo);
        assertLibre();
    }

    @Test
    void unRecojoVinculadoCanceladoNoImpideLaLiberacion() {
        Long ronda = pedidoDeMesa();
        Long recojo = pedido(TipoPedido.RECOJO, mesaId);
        cambiarEstado.ejecutar(recojo, restauranteId, EstadoPedido.CANCELADO);
        assertOcupada();

        entregar(ronda);
        pagar(ronda);

        assertLibre();
    }

    @Test
    void laMesaLiberadaSeVuelveAUsar() {
        Long a = pedidoDeMesa();
        entregar(a);
        pagar(a);
        assertLibre();

        pedidoDeMesa();

        assertOcupada();
    }

    @Test
    void pedidosSinMesaCobranYEntreganSinTocarNada() {
        Long delivery = pedido(TipoPedido.DELIVERY, null);
        Long recojo = pedido(TipoPedido.RECOJO, null);

        pagar(recojo);
        cambiarEstado.ejecutar(recojo, restauranteId, EstadoPedido.EN_PREPARACION);
        cambiarEstado.ejecutar(recojo, restauranteId, EstadoPedido.LISTO);
        cambiarEstado.ejecutar(recojo, restauranteId, EstadoPedido.ENTREGADO);
        cambiarEstado.ejecutar(delivery, restauranteId, EstadoPedido.CANCELADO);

        assertEquals(EstadoMesa.LIBRE, mesa().getEstado());
    }
}
