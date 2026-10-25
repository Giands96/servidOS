package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.ProductCatalogPort;
import com.servidos.v1.ordering.application.ProductoCatalogInfo;
import com.servidos.v1.ordering.application.mesa.AsignarMesaAPedidoUseCase;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.DetallePedidoMapper;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearPedidoUseCaseTest {

    @Mock
    PedidoJpaRepository pedidoRepository;

    @Mock
    DetallePedidoJpaRepository detalleRepository;

    @Mock
    ProductCatalogPort productoCatalog;

    @Mock
    EventPublisher eventPublisher;

    @Mock
    AsignarMesaAPedidoUseCase asignarMesa;

    CrearPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearPedidoUseCase(pedidoRepository, detalleRepository, productoCatalog,
                new PedidoMapper(), new DetallePedidoMapper(), eventPublisher, asignarMesa);
    }

    private static CrearPedidoCommand comando(Long productoId) {
        return new CrearPedidoCommand(TipoPedido.MESA, 3L, null, null,
                List.of(new CrearPedidoItem(productoId, 2, null)));
    }

    private void dadoPedidoGuardado() {
        when(pedidoRepository.save(any(PedidoJpaEntity.class))).thenAnswer(inv -> {
            PedidoJpaEntity e = inv.getArgument(0);
            e.setPedidoId(1L);
            return e;
        });
    }

    @Test
    void productoDisponibleCreaPedidoConTotal() {
        dadoPedidoGuardado();
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.of(new ProductoCatalogInfo("Pizza Muzzarella", new BigDecimal("25.00"), true)));

        var pedido = useCase.ejecutar(comando(7L), 10L, 5L);

        assertEquals(0, new BigDecimal("50.00").compareTo(pedido.getTotal()));
    }

    @Test
    void productoAgotadoDa400() {
        dadoPedidoGuardado();
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.of(new ProductoCatalogInfo("Pizza Muzzarella", new BigDecimal("25.00"), false)));

        assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 10L, 5L));
    }

    @Test
    void productoInexistenteDa400() {
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 10L, 5L));
    }

    @Test
    void mesaDeOtroRestauranteDa400YNoGuarda() {
        doThrow(new BusinessException("La mesa no existe")).when(asignarMesa).ejecutar(3L, TipoPedido.MESA, 99L);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 99L, 5L));

        assertEquals("La mesa no existe", ex.getMessage());
        org.mockito.Mockito.verify(pedidoRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void asignaLaMesaDelPedidoAntesDeGuardarlo() {
        dadoPedidoGuardado();
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.of(new ProductoCatalogInfo("Pizza Muzzarella", new BigDecimal("25.00"), true)));

        useCase.ejecutar(comando(7L), 10L, 5L);

        var orden = org.mockito.Mockito.inOrder(asignarMesa, pedidoRepository);
        orden.verify(asignarMesa).ejecutar(3L, TipoPedido.MESA, 10L);
        orden.verify(pedidoRepository).save(any(PedidoJpaEntity.class));
    }

    @Test
    void mesaSeValidaAunqueElPedidoNoSeaDeMesa() {
        var cmd = new CrearPedidoCommand(TipoPedido.DELIVERY, 3L, null, "Juan",
                List.of(new CrearPedidoItem(7L, 1, null)));
        doThrow(new BusinessException("La mesa no existe")).when(asignarMesa).ejecutar(3L, TipoPedido.DELIVERY, 99L);

        assertThrows(BusinessException.class, () -> useCase.ejecutar(cmd, 99L, 5L));
    }

    @Test
    void sinUsuarioDa400YNoGuarda() {
        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 10L, null));

        assertEquals("Usuario no identificado", ex.getMessage());
        org.mockito.Mockito.verify(pedidoRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void guardaQuienTomoElPedido() {
        dadoPedidoGuardado();
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.of(new ProductoCatalogInfo("Pizza Muzzarella", new BigDecimal("25.00"), true)));

        var pedido = useCase.ejecutar(comando(7L), 10L, 5L);

        assertEquals(5L, pedido.getUsuario_id());
    }

    private void dadoProductoDisponible() {
        dadoPedidoGuardado();
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.of(new ProductoCatalogInfo("Pizza Muzzarella", new BigDecimal("25.00"), true)));
    }

    private static CrearPedidoCommand comando(TipoPedido tipo, Long mesaId) {
        return new CrearPedidoCommand(tipo, mesaId, null, tipo == TipoPedido.DELIVERY ? "Juan" : null,
                List.of(new CrearPedidoItem(7L, 1, null)));
    }

    @ParameterizedTest
    @CsvSource({"MESA, 3", "RECOJO, 3"})
    void meseroTomaPedidosDeMesaYParaLlevarDeUnaMesa(TipoPedido tipo, Long mesaId) {
        dadoProductoDisponible();

        var pedido = useCase.ejecutar(comando(tipo, mesaId), 10L, 5L, "MESERO");

        assertEquals(tipo, pedido.getTipoPedido());
        org.mockito.Mockito.verify(asignarMesa).ejecutar(mesaId, tipo, 10L);
    }

    @ParameterizedTest
    @CsvSource(value = {"DELIVERY, 3", "DELIVERY, NULL", "RECOJO, NULL"}, nullValues = "NULL")
    void meseroNoTomaDeliveryNiRecojoSinMesa(TipoPedido tipo, Long mesaId) {
        var ex = assertThrows(ForbiddenException.class,
                () -> useCase.ejecutar(comando(tipo, mesaId), 10L, 5L, "MESERO"));

        assertEquals("El mesero solo puede tomar pedidos de mesa o para llevar de una mesa", ex.getMessage());
        org.mockito.Mockito.verifyNoInteractions(asignarMesa, pedidoRepository, eventPublisher);
    }

    @ParameterizedTest
    @CsvSource({"ADMINISTRADOR", "RECEPCION"})
    void administradorYRecepcionSiguenTomandoDeliverySinMesa(String rol) {
        dadoProductoDisponible();

        var pedido = useCase.ejecutar(comando(TipoPedido.DELIVERY, null), 10L, 5L, rol);

        assertEquals(TipoPedido.DELIVERY, pedido.getTipoPedido());
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "COCINERO", "CAJERO", "ROLE_MESERO", "SUPERADMIN"}, nullValues = "NULL")
    void unRolNuloODesconocidoNoTomaPedidos(String rol) {
        var ex = assertThrows(ForbiddenException.class,
                () -> useCase.ejecutar(comando(TipoPedido.DELIVERY, null), 10L, 5L, rol));

        assertEquals("Rol sin permiso para tomar pedidos", ex.getMessage());
        org.mockito.Mockito.verifyNoInteractions(asignarMesa, pedidoRepository, eventPublisher);
    }
}
