package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deshace la baja de una mesa. Si mientras tanto otra mesa activa tomó su número, se rechaza:
 * el admin tiene que liberar ese número antes de reactivarla.
 */
@Service
@RequiredArgsConstructor
public class ReactivarMesaUseCase {

    private final MesaJpaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public Mesa ejecutar(Long mesaId, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (mesaId == null) throw new BusinessException("Mesa no identificada");

        var entity = mesaRepository.bloquearPorIdYRestaurante(mesaId, restauranteId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada"));
        Mesa mesa = mesaMapper.toDomain(entity);
        mesa.reactivar();
        if (mesaRepository.existsByRestauranteIdAndNumeroAndActivaTrueAndMesaIdNot(
                restauranteId, mesa.getNumero(), mesaId)) {
            throw CrearMesaUseCase.duplicada(mesa.getNumero());
        }
        mesaMapper.updateEntity(mesa, entity);
        try {
            return mesaMapper.toDomain(mesaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Alta o reactivación simultánea con el mismo número: gana el índice único parcial.
            throw CrearMesaUseCase.duplicada(mesa.getNumero());
        }
    }
}
