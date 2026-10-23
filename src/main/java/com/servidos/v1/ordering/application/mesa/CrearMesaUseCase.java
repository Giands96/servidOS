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
public class CrearMesaUseCase {

    private final MesaJpaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public Mesa ejecutar(CrearMesaCommand cmd, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");

        Mesa mesa = Mesa.crear(restauranteId, cmd.numero(), cmd.capacidad());
        if (mesaRepository.existsByRestauranteIdAndNumero(restauranteId, mesa.getNumero())) {
            throw duplicada(mesa.getNumero());
        }
        try {
            return mesaMapper.toDomain(mesaRepository.saveAndFlush(mesaMapper.toEntity(mesa)));
        } catch (DataIntegrityViolationException e) {
            // Dos altas simultáneas con el mismo número: gana el UNIQUE(restaurante_id, numero).
            throw duplicada(mesa.getNumero());
        }
    }

    static BusinessException duplicada(Integer numero) {
        return new BusinessException("La mesa " + numero + " ya existe para este restaurante");
    }
}
