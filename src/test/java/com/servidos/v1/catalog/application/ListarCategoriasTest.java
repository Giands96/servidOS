package com.servidos.v1.catalog.application;

import com.servidos.v1.catalog.application.categoria.ListarCategoriasUseCase;
import com.servidos.v1.catalog.domain.Categoria;
import com.servidos.v1.catalog.domain.EstadoCategoria;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.CategoriaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ListarCategoriasTest {

    @Mock CategoriaJpaRepository categoriaRepository;
    @Mock CategoriaMapper categoriaMapper;

    private ListarCategoriasUseCase useCase() {
        return new ListarCategoriasUseCase(categoriaRepository, categoriaMapper);
    }

    @Test
    void sinTenantEs400() {
        var ex = assertThrows(BusinessException.class,
                () -> useCase().listar(null, PageRequest.of(0, 20)));
        assertEquals("Restaurante no identificado", ex.getMessage());
        verifyNoInteractions(categoriaRepository);
    }

    @Test
    void listaMapeaADominio() {
        var pageable = PageRequest.of(0, 20);
        var e = new CategoriaJpaEntity();
        e.setCategoriaId(7L);
        e.setRestauranteId(100L);
        e.setNombre("Criollos");
        when(categoriaRepository.findByRestauranteId(100L, pageable))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(categoriaMapper.toDomain(e)).thenReturn(Categoria.builder()
                .categoria_id(7L).restaurante_id(100L)
                .nombre("Criollos").estado(EstadoCategoria.HABILITADO).build());

        var result = useCase().listar(100L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("Criollos", result.getContent().get(0).getNombre());
    }
}
