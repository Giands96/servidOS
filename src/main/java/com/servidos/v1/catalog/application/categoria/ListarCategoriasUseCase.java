package com.servidos.v1.catalog.application.categoria;

import com.servidos.v1.catalog.domain.Categoria;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.CategoriaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ListarCategoriasUseCase {

    private final CategoriaJpaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    @Transactional(readOnly = true)
    public Page<Categoria> listar(Long restauranteId, Pageable pageable) {
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        return categoriaRepository.findByRestauranteId(restauranteId, pageable).map(categoriaMapper::toDomain);
    }
}
