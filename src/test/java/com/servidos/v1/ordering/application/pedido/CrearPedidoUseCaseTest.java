package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.ProductCatalogPort;
import com.servidos.v1.ordering.application.ProductoCatalogInfo;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.DetallePedidoMapper;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

    CrearPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearPedidoUseCase(pedidoRepository, detalleRepository, productoCatalog,
                new PedidoMapper(), new DetallePedidoMapper(), eventPublisher);
        lenient().when(pedidoRepository.existsMesaEnRestaurante(3L, 10L)).thenReturn(true);
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

        var pedido = useCase.ejecutar(comando(7L), 10L);

        assertEquals(new BigDecimal("50.00"), pedido.getTotal());
    }

    @Test
    void productoAgotadoDa400() {
        dadoPedidoGuardado();
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.of(new ProductoCatalogInfo("Pizza Muzzarella", new BigDecimal("25.00"), false)));

        assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 10L));
    }

    @Test
    void productoInexistenteDa400() {
        when(productoCatalog.findInfoByIdAndRestaurante(7L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 10L));
    }

    @Test
    void mesaDeOtroRestauranteDa400YNoGuarda() {
        when(pedidoRepository.existsMesaEnRestaurante(3L, 99L)).thenReturn(false);

        var ex = assertThrows(BusinessException.class, () -> useCase.ejecutar(comando(7L), 99L));

        assertEquals("La mesa no existe", ex.getMessage());
        org.mockito.Mockito.verify(pedidoRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void mesaSeValidaAunqueElPedidoNoSeaDeMesa() {
        var cmd = new CrearPedidoCommand(TipoPedido.DELIVERY, 3L, null, "Juan",
                List.of(new CrearPedidoItem(7L, 1, null)));
        when(pedidoRepository.existsMesaEnRestaurante(3L, 99L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> useCase.ejecutar(cmd, 99L));
    }
}
