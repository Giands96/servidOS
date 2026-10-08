package com.servidos.v1.ordering;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.pedido.CrearPedidoCommand;
import com.servidos.v1.ordering.application.pedido.CrearPedidoItem;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.TipoPedido;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Crear un pedido contra Postgres real. Los tests unitarios mockean el repositorio y no
 * ven las restricciones de la base (pedido.usuario_id es NOT NULL). Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrearPedidoIntegracionTest {

    @Autowired CrearPedidoUseCase crearPedido;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired ProductoJpaRepository productoRepository;
    @Autowired PedidoJpaRepository pedidoRepository;

    Long restauranteId;
    Long usuarioId;
    Long productoId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-pedido-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();

        var u = new UsuarioJpaEntity();
        u.setNombre("Recepcion");
        u.setApellido("IT");
        u.setEmail("it-pedido-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        var p = new ProductoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setNombre("Lomo saltado");
        p.setPrecio(new BigDecimal("25.00"));
        p.setEstado(EstadoProducto.DISPONIBLE);
        productoId = productoRepository.save(p).getProductoId();
    }

    @Test
    void creaElPedidoYRegistraQuienLoTomo() {
        var cmd = new CrearPedidoCommand(TipoPedido.RECOJO, null, null, null,
                List.of(new CrearPedidoItem(productoId, 2, null)));

        var pedido = crearPedido.ejecutar(cmd, restauranteId, usuarioId);
        pedidoRepository.flush();

        var guardado = pedidoRepository.findById(pedido.getPedido_id()).orElseThrow();
        assertEquals(usuarioId, guardado.getUsuarioId());
        assertEquals(0, new BigDecimal("50.00").compareTo(guardado.getTotal()));
    }
}
