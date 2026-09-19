package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ObtenerRestauranteUseCase {
    private final RestauranteJpaRepository restauranteRepository;
    private final SuscripcionJpaRepository suscripcionRepository;
    private final RestauranteMapper restauranteMapper;
    private final SuscripcionMapper suscripcionMapper;

    @Transactional(readOnly = true)
    public Restaurante obtener(Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        var entity = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new BusinessException("El restaurante no existe"));
        return restauranteMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public Suscripcion obtenerSuscripcion(Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        var entity = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(restauranteId)
                .orElseThrow(() -> new BusinessException("No se encontró suscripción para el restaurante"));
        return suscripcionMapper.toDomain(entity);
    }
}
