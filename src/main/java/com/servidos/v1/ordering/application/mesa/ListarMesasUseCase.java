package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarMesasUseCase {

    private final MesaJpaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    /** Mesas del restaurante por número; {@code estado} null = todas. */
    @Transactional(readOnly = true)
    public List<Mesa> listar(Long restauranteId, EstadoMesa estado) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        List<MesaJpaEntity> mesas = estado == null
                ? mesaRepository.findByRestauranteIdOrderByNumeroAsc(restauranteId)
                : mesaRepository.findByRestauranteIdAndEstadoOrderByNumeroAsc(restauranteId, estado);
        return mesas.stream().map(mesaMapper::toDomain).toList();
    }
}
