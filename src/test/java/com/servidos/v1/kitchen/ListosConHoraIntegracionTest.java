package com.servidos.v1.kitchen;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.kitchen.application.cocina.GestionarColaCocinaUseCase;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * "Listos para salir" muestra hace cuánto está listo cada pedido: GET /cocina/listos tiene
 * que traer listoAt, llegue a LISTO por cocina o por la API de pedidos. Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListosConHoraIntegracionTest {

    @Autowired GestionarColaCocinaUseCase cocina;
    @Autowired CambiarEstadoPedidoUseCase cambiarEstado;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired PedidoJpaRepository pedidoRepository;

    Long restauranteId;
    Long pedidoId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-listo-at-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();

        var u = new UsuarioJpaEntity();
        u.setNombre("Cocina");
        u.setApellido("IT");
        u.setEmail("it-listo-at-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        Long usuarioId = usuarioRepository.save(u).getUsuarioId();

        var p = new PedidoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setUsuarioId(usuarioId);
        p.setTipoPedido(TipoPedido.RECOJO);
        p.setEstado(EstadoPedido.EN_PREPARACION);
        p.setTotal(new BigDecimal("10.00"));
        pedidoId = pedidoRepository.saveAndFlush(p).getPedidoId();
    }

    private void verificarListoAt(LocalDateTime antes) {
        pedidoRepository.flush();
        var listos = cocina.listarListos(restauranteId);
        assertEquals(1, listos.size());
        var listoAt = listos.get(0).getListo_at();
        assertNotNull(listoAt, "GET /cocina/listos tiene que traer cuándo pasó a LISTO");
        assertFalse(listoAt.isBefore(antes));
    }

    @Test
    void enPreparacionNoTieneHoraDeListo() {
        assertNull(cocina.listarEnPreparacion(restauranteId).get(0).getListo_at());
    }

    @Test
    void marcarListoDesdeCocinaRegistraLaHora() {
        var antes = LocalDateTime.now().withNano(0);
        cocina.marcarListo(pedidoId, restauranteId);
        verificarListoAt(antes);
    }

    @Test
    void pasarAListoPorLaApiDePedidosTambienRegistraLaHora() {
        var antes = LocalDateTime.now().withNano(0);
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.LISTO);
        verificarListoAt(antes);
    }

    @Test
    void salirDeListoConservaLaHora() {
        cocina.marcarListo(pedidoId, restauranteId);
        pedidoRepository.flush();
        var listoAt = pedidoRepository.findById(pedidoId).orElseThrow().getListoAt();

        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.ENTREGADO);
        pedidoRepository.flush();

        assertEquals(listoAt, pedidoRepository.findById(pedidoId).orElseThrow().getListoAt());
    }
}
