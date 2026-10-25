package com.servidos.v1.ordering;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.kitchen.api.CocinaController;
import com.servidos.v1.kitchen.api.dto.PreparacionPedidoResponse;
import com.servidos.v1.ordering.api.PedidoController;
import com.servidos.v1.ordering.api.dto.PedidoListadoResponse;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaCommand;
import com.servidos.v1.ordering.application.mesa.ActualizarMesaUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.api.CuentaMesaController;
import com.servidos.v1.payment.api.dto.PagarCuentaMesaRequest;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.reporting.api.ReporteController;
import com.servidos.v1.reporting.api.dto.VentaPedidoResponse;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Las respuestas de pedidos traen el número de la mesa ({@code numeroMesa}), no solo su id:
 * listado, pedido, cocina, cobro de cuenta y reporte de ventas. Es el número actual de la mesa,
 * aunque esté dada de baja; null sin mesa. Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NumeroMesaIntegracionTest {

    @Autowired PedidoController pedidoController;
    @Autowired CocinaController cocinaController;
    @Autowired CuentaMesaController cuentaController;
    @Autowired ReporteController reporteController;
    @Autowired ActualizarMesaUseCase actualizarMesa;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired MesaJpaRepository mesaRepository;
    @Autowired PedidoJpaRepository pedidoRepository;
    @Autowired PagoJpaRepository pagoRepository;

    Long restauranteId;
    Long usuarioId;
    Long mesaId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-numero-mesa-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();

        var u = new UsuarioJpaEntity();
        u.setNombre("Caja");
        u.setApellido("IT");
        u.setEmail("it-numero-mesa-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        mesaId = mesa(7, true);

        TenantContext.setRestauranteId(restauranteId);
        CurrentUser.setCurrentUser(usuarioId);
        como("ADMINISTRADOR");
    }

    /** Los controllers van con {@code @PreAuthorize}: se llaman con un rol real en el contexto. */
    private void como(String rol) {
        CurrentUser.setRole(rol);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuarioId, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol))));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        CurrentUser.clear();
        SecurityContextHolder.clearContext();
    }

    private Long mesa(int numero, boolean activa) {
        var m = new MesaJpaEntity();
        m.setRestauranteId(restauranteId);
        m.setNumero(numero);
        m.setCapacidad(4);
        m.setEstado(EstadoMesa.LIBRE);
        m.setActiva(activa);
        return mesaRepository.saveAndFlush(m).getMesaId();
    }

    private Long pedido(TipoPedido tipo, Long mesa, EstadoPedido estado) {
        var p = new PedidoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setUsuarioId(usuarioId);
        p.setMesaId(mesa);
        p.setTipoPedido(tipo);
        p.setEstado(estado);
        p.setTotal(new BigDecimal("20.00"));
        return pedidoRepository.saveAndFlush(p).getPedidoId();
    }

    private Map<Long, Integer> numerosDelListado() {
        return pedidoController.listar(null, null, false, 0, 20).getBody().getContent().stream()
                .collect(Collectors.toMap(PedidoListadoResponse::pedidoId,
                        l -> l.numeroMesa() == null ? -1 : l.numeroMesa()));
    }

    @Test
    void elListadoTraeElNumeroDeLaMesaONullSinMesa() {
        Long deMesa = pedido(TipoPedido.MESA, mesaId, EstadoPedido.PENDIENTE);
        Long recojoDeMesa = pedido(TipoPedido.RECOJO, mesaId, EstadoPedido.PENDIENTE);
        Long recojoSinMesa = pedido(TipoPedido.RECOJO, null, EstadoPedido.PENDIENTE);
        Long delivery = pedido(TipoPedido.DELIVERY, null, EstadoPedido.PENDIENTE);

        var numeros = numerosDelListado();

        assertEquals(7, numeros.get(deMesa));
        assertEquals(7, numeros.get(recojoDeMesa));
        assertEquals(-1, numeros.get(recojoSinMesa));
        assertEquals(-1, numeros.get(delivery));
    }

    @Test
    void elListadoPorCobrarTambienTraeElNumero() {
        Long deMesa = pedido(TipoPedido.MESA, mesaId, EstadoPedido.ENTREGADO);

        var fila = pedidoController.listar(null, null, true, 0, 20).getBody().getContent().get(0);

        assertEquals(deMesa, fila.pedidoId());
        assertEquals(mesaId, fila.mesaId());
        assertEquals(7, fila.numeroMesa());
    }

    @Test
    void renumerarLaMesaCambiaElNumeroDeSusPedidosAbiertos() {
        Long deMesa = pedido(TipoPedido.MESA, mesaId, EstadoPedido.PENDIENTE);

        actualizarMesa.ejecutar(new ActualizarMesaCommand(mesaId, 12, 4), restauranteId);
        mesaRepository.flush();

        assertEquals(12, numerosDelListado().get(deMesa));
    }

    @Test
    void unaMesaDadaDeBajaConservaSuNumero() {
        Long inactiva = mesa(9, false);
        Long pedidoId = pedido(TipoPedido.MESA, inactiva, EstadoPedido.ENTREGADO);

        assertEquals(9, numerosDelListado().get(pedidoId));
    }

    @Test
    void confirmarYCambiarEstadoDevuelvenElNumero() {
        Long deMesa = pedido(TipoPedido.MESA, mesaId, EstadoPedido.PENDIENTE);
        Long delivery = pedido(TipoPedido.DELIVERY, null, EstadoPedido.PENDIENTE);

        var confirmado = pedidoController.confirmar(deMesa).getBody();
        var sinMesa = pedidoController.confirmar(delivery).getBody();

        assertEquals(mesaId, confirmado.mesaId());
        assertEquals(7, confirmado.numeroMesa());
        assertNull(sinMesa.numeroMesa());
    }

    @Test
    void elCocineroVeElNumeroEnLaColaYEnLosListos() {
        Long enPreparacion = pedido(TipoPedido.MESA, mesaId, EstadoPedido.EN_PREPARACION);
        Long listoDeRecojo = pedido(TipoPedido.RECOJO, mesaId, EstadoPedido.LISTO);
        pedido(TipoPedido.DELIVERY, null, EstadoPedido.LISTO);
        como("COCINERO");

        List<PreparacionPedidoResponse> cola = cocinaController.cola().getBody();
        List<PreparacionPedidoResponse> listos = cocinaController.listos().getBody();

        assertEquals(enPreparacion, cola.get(0).pedidoId());
        assertEquals(7, cola.get(0).numeroMesa());
        var numerosListos = listos.stream().collect(Collectors.toMap(PreparacionPedidoResponse::pedidoId,
                l -> l.numeroMesa() == null ? -1 : l.numeroMesa()));
        assertEquals(7, numerosListos.get(listoDeRecojo));
        assertEquals(1, numerosListos.values().stream().filter(n -> n == -1).count());
    }

    @Test
    void cobrarLaCuentaDevuelveElNumeroDeLaMesa() {
        Long deMesa = pedido(TipoPedido.MESA, mesaId, EstadoPedido.ENTREGADO);

        var cuenta = cuentaController.pagar(mesaId,
                new PagarCuentaMesaRequest(MetodoPago.YAPE, null, null, List.of(deMesa))).getBody();

        assertEquals(mesaId, cuenta.mesaId());
        assertEquals(7, cuenta.numeroMesa());
    }

    @Test
    void elReporteDeVentasTraeMesaYNumero() {
        Long deMesa = pedido(TipoPedido.MESA, mesaId, EstadoPedido.ENTREGADO);
        Long delivery = pedido(TipoPedido.DELIVERY, null, EstadoPedido.ENTREGADO);
        LocalDate hoy = LocalDate.now();
        pagado(deMesa, hoy);
        pagado(delivery, hoy);

        var ventas = reporteController.ventas(Periodo.DIARIO, hoy, OrdenVentas.MAS_RECIENTES, 0, 20)
                .getBody().ventas().getContent();

        var porPedido = ventas.stream().collect(Collectors.toMap(VentaPedidoResponse::pedidoId, v -> v));
        assertEquals(mesaId, porPedido.get(deMesa).mesaId());
        assertEquals(7, porPedido.get(deMesa).numeroMesa());
        assertNull(porPedido.get(delivery).mesaId());
        assertNull(porPedido.get(delivery).numeroMesa());
    }

    private void pagado(Long pedidoId, LocalDate dia) {
        var pg = new PagoJpaEntity();
        pg.setPedidoId(pedidoId);
        pg.setRestauranteId(restauranteId);
        pg.setUsuarioId(usuarioId);
        pg.setMetodoPago(MetodoPago.YAPE);
        pg.setMonto(new BigDecimal("20.00"));
        pg.setEstado(EstadoPago.PAGADO);
        pg.setFechaPago(dia.atTime(12, 0));
        pagoRepository.saveAndFlush(pg);
    }
}
