package com.servidos.v1.catalog.application.categoria;

import com.servidos.v1.catalog.domain.Categoria;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.CategoriaJpaRepository;
import com.servidos.v1.catalog.infrastructure.mapper.CategoriaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GestionarCategoriaUseCase {

    private final CategoriaJpaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    private void validar(GestionarCategoriaCommand cmd, Long restauranteId) {
        if(cmd.nombre() == null || cmd.nombre().trim().isEmpty()) {
            throw new BusinessException("El nombre de la categoría es obligatorio");
        }
        if (cmd.estado() == null) {
            throw new BusinessException("El estado de la categoría es obligatorio");
        }
        if (categoriaRepository.existsByNombreAndRestauranteId(cmd.nombre().trim(), restauranteId)) {
            throw new BusinessException("La categoría ya existe para este restaurante");
        }

    }

    @Transactional
    public Categoria ejecutar(GestionarCategoriaCommand cmd, Long restauranteId) {
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        validar(cmd, restauranteId);

        Categoria domain = Categoria.crear(restauranteId, cmd.nombre().trim(), cmd.estado());
        // Mapear a JPA Entity y persistir
        CategoriaJpaEntity entity = categoriaMapper.toEntity(domain);
        CategoriaJpaEntity saved = categoriaRepository.save(entity);
        // Mapear de vuelta a dominio para retornar
        return categoriaMapper.toDomain(saved);
    }


}