package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.event.RestauranteEstadoCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CambiarEstadoRestauranteUseCase {

    private final RestauranteJpaRepository restauranteRepository;
    private final RestauranteMapper restauranteMapper;
    private final EventPublisher eventPublisher;

    /**
     * Activa o suspende un restaurante. Solo la plataforma lo invoca: es lo que le
     * permite a un SUPERADMIN reactivar a un cliente que ya pagó, dado que el cobro se
     * resuelve por fuera del sistema y acá no hay forma de verificarlo.
     *
     * <p>No usa TenantContext a propósito: es una operación de plataforma sobre un id
     * explícito, no sobre "el restaurante de la sesión".
     *
     * <p>No toca la suscripción. Suspender no cancela el plan: son palancas
     * independientes y separarlas permite reactivar sin perder el historial de cobros.
     */
    @Transactional
    public Restaurante ejecutar(Long restauranteId, EstadoRestaurante nuevoEstado) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        if (nuevoEstado == null) throw new BusinessException("El estado es obligatorio");

        var entity = restauranteRepository.findById(restauranteId)
                .orElseThrow(() -> new BusinessException("El restaurante no existe"));

        EstadoRestaurante anterior = entity.getEstado();
        if (anterior == nuevoEstado) {
            throw new BusinessException("El restaurante ya está " + nuevoEstado.name());
        }
        entity.setEstado(nuevoEstado);
        var guardado = restauranteRepository.save(entity);

        eventPublisher.publish(new RestauranteEstadoCambiadoEvent(restauranteId, anterior, nuevoEstado));
        return restauranteMapper.toDomain(guardado);
    }
}