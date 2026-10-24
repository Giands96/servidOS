package com.servidos.v1.ordering;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.application.mesa.DesactivarMesaUseCase;
import com.servidos.v1.ordering.application.mesa.ListarMesasUseCase;
import com.servidos.v1.ordering.application.mesa.OcuparMesaCommand;
import com.servidos.v1.ordering.application.mesa.OcuparMesaUseCase;
import com.servidos.v1.ordering.application.mesa.ReactivarMesaUseCase;
import com.servidos.v1.ordering.application.pedido.CambiarEstadoPedidoUseCase;
import com.servidos.v1.ordering.application.pedido.CrearPedidoCommand;
import com.servidos.v1.ordering.application.pedido.CrearPedidoItem;
import com.servidos.v1.ordering.application.pedido.CrearPedidoUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Baja lógica de mesas contra Postgres real: el índice único parcial (V15), el lock de fila y la
 * consulta de pedidos abiertos solo se ejercitan con la base de verdad. Rollback por test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MesaBajaLogicaIntegracionTest {

    @Autowired CrearMesaUseCase crearMesa;
    @Autowired DesactivarMesaUseCase desactivarMesa;
    @Autowired ReactivarMesaUseCase reactivarMesa;
    @Autowired ListarMesasUseCase listarMesas;
    @Autowired OcuparMesaUseCase ocuparMesa;
    @Autowired CrearPedidoUseCase crearPedido;
    @Autowired CambiarEstadoPedidoUseCase cambiarEstado;
    @Autowired MesaJpaRepository mesaRepository;
    @Autowired PedidoJpaRepository pedidoRepository;
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
        restauranteId = nuevoRestaurante("it-baja-a-");
        otroRestauranteId = nuevoRestaurante("it-baja-b-");

        var u = new UsuarioJpaEntity();
        u.setNombre("Recepcion");
        u.setApellido("IT");
        u.setEmail("it-baja-" + System.nanoTime() + "@test.local");
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

    private CrearPedidoCommand pedidoDeMesa() {
        return new CrearPedidoCommand(TipoPedido.MESA, mesaId, null, null,
                List.of(new CrearPedidoItem(productoId, 1, null)));
    }

    @Test
    void darDeBajaLiberaElNumeroParaUnaMesaNueva() {
        desactivarMesa.ejecutar(mesaId, restauranteId);

        var nueva = crearMesa.ejecutar(new CrearMesaCommand(1, 6), restauranteId);

        var activas = listarMesas.listar(restauranteId, null);
        assertEquals(List.of(nueva.getMesa_id()), activas.stream().map(m -> m.getMesa_id()).toList());
        var inactivas = listarMesas.listarInactivas(restauranteId);
        assertEquals(List.of(mesaId), inactivas.stream().map(m -> m.getMesa_id()).toList());
        assertFalse(inactivas.get(0).isActiva());
    }

    @Test
    void listarInactivasNoMuestraMesasDeOtroRestaurante() {
        desactivarMesa.ejecutar(mesaId, restauranteId);

        assertEquals(0, listarMesas.listarInactivas(otroRestauranteId).size());
    }

    @Test
    void reactivarDevuelveLaMesaAlListado() {
        desactivarMesa.ejecutar(mesaId, restauranteId);

        var mesa = reactivarMesa.ejecutar(mesaId, restauranteId);

        assertTrue(mesa.isActiva());
        assertEquals(1, listarMesas.listar(restauranteId, null).size());
        assertEquals(0, listarMesas.listarInactivas(restauranteId).size());
    }

    @Test
    void reactivarFallaSiOtraMesaActivaUsaElNumero() {
        desactivarMesa.ejecutar(mesaId, restauranteId);
        crearMesa.ejecutar(new CrearMesaCommand(1, 6), restauranteId);

        var ex = assertThrows(BusinessException.class, () -> reactivarMesa.ejecutar(mesaId, restauranteId));

        assertEquals("La mesa 1 ya existe para este restaurante", ex.getMessage());
        assertFalse(mesaRepository.findById(mesaId).orElseThrow().isActiva());
    }

    @Test
    void desactivarUnaMesaOcupadaSeRechaza() {
        ocuparMesa.ejecutar(new OcuparMesaCommand(mesaId, 2), restauranteId);

        var ex = assertThrows(BusinessException.class, () -> desactivarMesa.ejecutar(mesaId, restauranteId));

        assertEquals("No se puede dar de baja una mesa ocupada", ex.getMessage());
        assertTrue(mesaRepository.findById(mesaId).orElseThrow().isActiva());
    }

    @Test
    void desactivarUnaMesaConPedidosAbiertosSeRechaza() {
        // Mesa LIBRE pero con un pedido de mesa abierto: el estado solo no alcanza.
        var p = new PedidoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setUsuarioId(usuarioId);
        p.setMesaId(mesaId);
        p.setTipoPedido(TipoPedido.MESA);
        p.setEstado(EstadoPedido.PENDIENTE);
        p.setTotal(new BigDecimal("25.00"));
        pedidoRepository.saveAndFlush(p);

        var ex = assertThrows(BusinessException.class, () -> desactivarMesa.ejecutar(mesaId, restauranteId));

        assertEquals("La mesa tiene pedidos abiertos", ex.getMessage());
        assertTrue(mesaRepository.findById(mesaId).orElseThrow().isActiva());
    }

    @Test
    void unaMesaInactivaNoRecibePedidosDeMesaNuevos() {
        desactivarMesa.ejecutar(mesaId, restauranteId);

        var ex = assertThrows(BusinessException.class,
                () -> crearPedido.ejecutar(pedidoDeMesa(), restauranteId, usuarioId));

        assertEquals("La mesa está inactiva", ex.getMessage());
        assertEquals(EstadoMesa.LIBRE, mesaRepository.findById(mesaId).orElseThrow().getEstado());
    }

    @Test
    void unaMesaInactivaNoRecibePedidosDeRecojoConEsaMesa() {
        desactivarMesa.ejecutar(mesaId, restauranteId);
        var recojo = new CrearPedidoCommand(TipoPedido.RECOJO, mesaId, null, null,
                List.of(new CrearPedidoItem(productoId, 1, null)));

        var ex = assertThrows(BusinessException.class,
                () -> crearPedido.ejecutar(recojo, restauranteId, usuarioId));

        assertEquals("La mesa está inactiva", ex.getMessage());
    }

    @Test
    void desactivarUnaMesaDeOtroRestauranteSeRechaza() {
        var ex = assertThrows(BusinessException.class, () -> desactivarMesa.ejecutar(mesaId, otroRestauranteId));

        assertEquals("Mesa no encontrada", ex.getMessage());
        assertTrue(mesaRepository.findById(mesaId).orElseThrow().isActiva());
    }

    @Test
    void losPedidosHistoricosDeUnaMesaDadaDeBajaSiguenIntactos() {
        Long pedidoId = crearPedido.ejecutar(pedidoDeMesa(), restauranteId, usuarioId).getPedido_id();
        cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.CANCELADO);

        desactivarMesa.ejecutar(mesaId, restauranteId);

        var pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertEquals(mesaId, pedido.getMesaId());
        assertEquals(EstadoPedido.CANCELADO, pedido.getEstado());
        assertTrue(mesaRepository.findById(mesaId).isPresent());
    }
}
