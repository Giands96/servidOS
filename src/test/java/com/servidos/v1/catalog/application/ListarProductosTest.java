package com.servidos.v1.catalog.application;

import com.servidos.v1.catalog.application.producto.ListarProductosUseCase;
import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.ProductoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ListarProductosTest {

    @Mock ProductoJpaRepository productoRepository;
    @Mock ProductoMapper productoMapper;

    private ListarProductosUseCase useCase() {
        return new ListarProductosUseCase(productoRepository, productoMapper);
    }

    private static ProductoJpaEntity entity(Long id) {
        var e = new ProductoJpaEntity();
        e.setProductoId(id);
        e.setRestauranteId(100L);
        e.setNombre("Ceviche");
        return e;
    }

    private static Producto domain(Long id) {
        return Producto.builder().producto_id(id).restaurante_id(100L).nombre("Ceviche")
                .precio(new BigDecimal("25.00")).estado(EstadoProducto.DISPONIBLE).build();
    }

    @Test
    void sinTenantEs400() {
        var ex = assertThrows(BusinessException.class,
                () -> useCase().listar(null, null, Pageable.unpaged()));
        assertEquals("Restaurante no identificado", ex.getMessage());
        verifyNoInteractions(productoRepository);
    }

    @Test
    void sinCategoriaPaginaElTenant() {
        var pageable = PageRequest.of(0, 20);
        when(productoRepository.findByRestauranteId(100L, pageable))
                .thenReturn(new PageImpl<>(List.of(entity(1L), entity(2L))));
        when(productoMapper.toDomain(any())).thenAnswer(i -> domain(
                ((ProductoJpaEntity) i.getArgument(0)).getProductoId()));

        var result = useCase().listar(100L, null, pageable);

        assertEquals(2, result.getTotalElements());
        verify(productoRepository, never()).findByRestauranteIdAndCategoriaId(any(), any(), any());
    }

    @Test
    void conCategoriaFiltra() {
        var pageable = PageRequest.of(0, 20);
        when(productoRepository.findByRestauranteIdAndCategoriaId(100L, 7L, pageable))
                .thenReturn(new PageImpl<>(List.of(entity(1L))));
        when(productoMapper.toDomain(any())).thenReturn(domain(1L));

        var result = useCase().listar(100L, 7L, pageable);

        assertEquals(1, result.getTotalElements());
        verify(productoRepository, never()).findByRestauranteId(any(), any());
    }

    @Test
    void obtenerDeOtroTenantEs400() {
        when(productoRepository.findByProductoIdAndRestauranteId(9L, 100L))
                .thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> useCase().obtener(100L, 9L));
        assertEquals("Producto no encontrado", ex.getMessage());
    }

    @Test
    void obtenerFelizMapeaADominio() {
        when(productoRepository.findByProductoIdAndRestauranteId(1L, 100L))
                .thenReturn(Optional.of(entity(1L)));
        when(productoMapper.toDomain(any())).thenReturn(domain(1L));

        assertEquals(1L, useCase().obtener(100L, 1L).getProducto_id());
    }
}
