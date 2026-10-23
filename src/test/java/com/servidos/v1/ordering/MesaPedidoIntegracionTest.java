package com.servidos.v1.ordering;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.OcuparMesaCommand;
import com.servidos.v1.ordering.application.mesa.OcuparMesaUseCase;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoCommand;
import com.servidos.v1.ordering.application.pedido.CrearPedidoItem;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Los pedidos de mesa ocupan la mesa y la cancelación la libera, contra Postgres real.
 * Rollback por test. El ciclo con pago está en {@code MesaCicloCompletoIntegracionTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MesaPedidoIntegracionTest {

    @Autowired CrearMesaUseCase crearMesa;
    @Autowired OcuparMesaUseCase ocuparMesa;
    @Autowired CrearPedidoUseCase crearPedido;
    @Autowired CambiarEstadoPedidoUseCase cambiarEstado;
    @Autowired MesaJpaRepository mesaRepository;
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
        restauranteId = nuevoRestaurante("it-mesa-pedido-a-");
        otroRestauranteId = nuevoRestaurante("it-mesa-pedido-b-");

        var u = new UsuarioJpaEntity();
        u.setNombre("Recepcion");
        u.setApellido("IT");
        u.setEmail("it-mesa-pedido-" + System.nanoTime() + "@test.local");
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
    }

    private Long nuevoRestaurante(String prefijo) {
        var r = new RestauranteJpaEntity();
        r.setSlug(prefijo + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        return restauranteRepository.save(r).getRestauranteId();
    }

    private Long pedidoDeMesa() {
        var cmd = new CrearPedidoCommand(TipoPedido.MESA, mesaId, null, null,
                List.of(new CrearPedidoItem(productoId, 1, null)));
        return crearPedido.ejecutar(cmd, restauranteId, usuarioId).getPedido_id();
    }

    private void cancelar(Long pedidoId) {
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.CANCELADO);
    }

    private EstadoMesa estadoMesa() {
        return mesaRepository.findById(mesaId).orElseThrow().getEstado();
    }

    @Test
    void elPrimerPedidoDeMesaOcupaLaMesaLibreSinComensales() {
        pedidoDeMesa();

        var mesa = mesaRepository.findById(mesaId).orElseThrow();
        assertEquals(EstadoMesa.OCUPADA, mesa.getEstado());
        assertNull(mesa.getComensales());
    }

    @Test
    void variosPedidosComparteLaMesaOcupadaYRespetanLosComensales() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 3), restauranteId);

        pedidoDeMesa();
        pedidoDeMesa();

        var mesa = mesaRepository.findById(mesaId).orElseThrow();
        assertEquals(EstadoMesa.OCUPADA, mesa.getEstado());
        assertEquals(3, mesa.getComensales());
    }

    @Test
    void unPedidoQueNoEsDeMesaNoOcupaLaMesa() {
        var cmd = new CrearPedidoCommand(TipoPedido.DELIVERY, mesaId, null, "Juan",
                List.of(new CrearPedidoItem(productoId, 1, null)));

        crearPedido.ejecutar(cmd, restauranteId, usuarioId);

        assertEquals(EstadoMesa.LIBRE, estadoMesa());
    }

    @Test
    void laMesaDeOtroRestauranteSeRechazaYNoCambia() {
        var cmd = new CrearPedidoCommand(TipoPedido.MESA, mesaId, null, null,
                List.of(new CrearPedidoItem(productoId, 1, null)));

        var ex = assertThrows(BusinessException.class,
                () -> crearPedido.ejecutar(cmd, otroRestauranteId, usuarioId));

        assertEquals("La mesa no existe", ex.getMessage());
        assertEquals(EstadoMesa.LIBRE, estadoMesa());
    }

    @Test
    void cancelarElUnicoPedidoLiberaLaMesaYLimpiaLosComensales() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 3), restauranteId);
        Long pedidoId = pedidoDeMesa();

        cancelar(pedidoId);

        var mesa = mesaRepository.findById(mesaId).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getComensales());
    }

    @Test
    void cancelarUnPedidoConOtroAbiertoDejaLaMesaOcupada() {
        Long primero = pedidoDeMesa();
        pedidoDeMesa();

        cancelar(primero);

        assertEquals(EstadoMesa.OCUPADA, estadoMesa());
    }
}
