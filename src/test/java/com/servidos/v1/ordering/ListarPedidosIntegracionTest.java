package com.servidos.v1.ordering;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.pedido.ListarPedidosUseCase;
import com.servidos.v1.ordering.application.pedido.ListarPedidosUseCase.PedidoListado;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaEntity;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * La consulta nativa del listado contra Postgres: filtros opcionales en null, "por cobrar"
 * mirando la tabla pago, día de creación y aislamiento por restaurante. Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListarPedidosIntegracionTest {

    @Autowired ListarPedidosUseCase listar;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired PedidoJpaRepository pedidoRepository;
    @Autowired PagoJpaRepository pagoRepository;
    @Autowired JdbcTemplate jdbc;

    Long restauranteId;
    Long usuarioId;
    Long pendiente, entregadoPagado, cancelado, entregadoReembolsado, deAyer;

    private Long restaurante() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-listar-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        return restauranteRepository.save(r).getRestauranteId();
    }

    private Long pedido(Long restaurante, EstadoPedido estado) {
        var p = new PedidoJpaEntity();
        p.setRestauranteId(restaurante);
        p.setUsuarioId(usuarioId);
        p.setTipoPedido(TipoPedido.RECOJO);
        p.setEstado(estado);
        p.setTotal(new BigDecimal("10.00"));
        return pedidoRepository.saveAndFlush(p).getPedidoId();
    }

    private void pago(Long pedidoId, EstadoPago estado) {
        var pago = new PagoJpaEntity();
        pago.setPedidoId(pedidoId);
        pago.setRestauranteId(restauranteId);
        pago.setUsuarioId(usuarioId);
        pago.setMetodoPago(MetodoPago.YAPE);
        pago.setMonto(new BigDecimal("10.00"));
        pago.setEstado(estado);
        pago.setFechaPago(LocalDateTime.now());
        pagoRepository.saveAndFlush(pago);
    }

    @BeforeEach
    void setUp() {
        restauranteId = restaurante();
        var u = new UsuarioJpaEntity();
        u.setNombre("Caja");
        u.setApellido("IT");
        u.setEmail("it-listar-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        deAyer = pedido(restauranteId, EstadoPedido.EN_PREPARACION);
        jdbc.update("UPDATE pedido SET created_at = created_at - interval '1 day' WHERE pedido_id = ?", deAyer);
        pendiente = pedido(restauranteId, EstadoPedido.PENDIENTE);
        entregadoPagado = pedido(restauranteId, EstadoPedido.ENTREGADO);
        pago(entregadoPagado, EstadoPago.PAGADO);
        cancelado = pedido(restauranteId, EstadoPedido.CANCELADO);
        entregadoReembolsado = pedido(restauranteId, EstadoPedido.ENTREGADO);
        pago(entregadoReembolsado, EstadoPago.REEMBOLSADO);

        pedido(restaurante(), EstadoPedido.PENDIENTE); // otro restaurante: nunca aparece
    }

    private List<Long> ids(List<PedidoListado> filas) {
        return filas.stream().map(f -> f.pedido().getPedido_id()).toList();
    }

    @Test
    void sinFiltrosTraeTodoElRestauranteDelMasNuevoAlMasViejo() {
        var page = listar.listar(restauranteId, null, null, false, 0, 20);

        assertEquals(List.of(entregadoReembolsado, cancelado, entregadoPagado, pendiente, deAyer), ids(page.getContent()));
        assertEquals("REEMBOLSADO", page.getContent().get(0).estadoPago());
        assertEquals("PAGADO", page.getContent().get(2).estadoPago());
        assertNull(page.getContent().get(3).estadoPago());
    }

    @Test
    void filtraPorEstado() {
        var page = listar.listar(restauranteId, EstadoPedido.ENTREGADO, null, false, 0, 20);
        assertEquals(List.of(entregadoReembolsado, entregadoPagado), ids(page.getContent()));
    }

    /** Por cobrar: ni cancelados, ni pagados, ni reembolsados (un reembolsado no se vuelve a cobrar). */
    @Test
    void porCobrarSoloTraeLosQueSePuedenCobrar() {
        var page = listar.listar(restauranteId, null, null, true, 0, 20);
        assertEquals(List.of(pendiente, deAyer), ids(page.getContent()));
    }

    @Test
    void filtraPorDiaDeCreacion() {
        var hoy = listar.listar(restauranteId, null, LocalDate.now(), false, 0, 20);
        var ayer = listar.listar(restauranteId, null, LocalDate.now().minusDays(1), false, 0, 20);

        assertEquals(4, hoy.getTotalElements());
        assertEquals(List.of(deAyer), ids(ayer.getContent()));
    }

    @Test
    void pagina() {
        var page = listar.listar(restauranteId, null, null, false, 1, 2);

        assertEquals(5, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(List.of(entregadoPagado, pendiente), ids(page.getContent()));
    }
}
