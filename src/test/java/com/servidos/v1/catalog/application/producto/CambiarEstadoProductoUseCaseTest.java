package com.servidos.v1.catalog.application.producto;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.ProductoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CambiarEstadoProductoUseCaseTest {

    @Mock
    ProductoJpaRepository productoRepository;

    CambiarEstadoProductoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CambiarEstadoProductoUseCase(productoRepository, new ProductoMapper());
    }

    private static ProductoJpaEntity producto(EstadoProducto estado) {
        ProductoJpaEntity e = new ProductoJpaEntity();
        e.setProductoId(1L);
        e.setRestauranteId(10L);
        e.setNombre("Ceviche");
        e.setPrecio(new BigDecimal("25.00"));
        e.setEstado(estado);
        return e;
    }

    @Test
    void cocinaMarcaAgotadoYDisponible() {
        when(productoRepository.findByProductoIdAndRestauranteId(1L, 10L))
                .thenReturn(Optional.of(producto(EstadoProducto.DISPONIBLE)));
        when(productoRepository.save(any(ProductoJpaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertEquals(EstadoProducto.AGOTADO,
                useCase.ejecutar(1L, 10L, EstadoProducto.AGOTADO).getEstado());
    }

    @Test
    void inexistenteOCrossTenantDa400() {
        when(productoRepository.findByProductoIdAndRestauranteId(1L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 10L, EstadoProducto.AGOTADO));
    }

    @Test
    void estadoObligatorio() {
        assertThrows(BusinessException.class,
                () -> useCase.ejecutar(1L, 10L, null));
    }
}
