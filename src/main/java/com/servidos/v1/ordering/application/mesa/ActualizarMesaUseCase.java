package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActualizarMesaUseCase {

    private final MesaJpaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public Mesa ejecutar(ActualizarMesaCommand cmd, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (cmd.mesaId() == null) throw new BusinessException("Mesa no identificada");

        // Lock: la capacidad se compara contra los comensales, que ocupar/liberar también tocan.
        var entity = mesaRepository.bloquearPorIdYRestaurante(cmd.mesaId(), restauranteId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada"));
        Mesa mesa = mesaMapper.toDomain(entity);
        mesa.actualizar(cmd.numero(), cmd.capacidad());
        if (mesaRepository.existsByRestauranteIdAndNumeroAndMesaIdNot(restauranteId, mesa.getNumero(), mesa.getMesa_id())) {
            throw CrearMesaUseCase.duplicada(mesa.getNumero());
        }
        mesaMapper.updateEntity(mesa, entity);
        try {
            return mesaMapper.toDomain(mesaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throw CrearMesaUseCase.duplicada(mesa.getNumero());
        }
    }
}
