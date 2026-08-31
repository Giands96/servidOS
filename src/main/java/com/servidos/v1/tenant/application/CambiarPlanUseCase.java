package com.servidos.v1.tenant.application;

import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.domain.event.PlanCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CambiarPlanUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final PlanJpaRepository planRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final SuscripcionMapper suscripcionMapper;
    private final EventPublisher eventPublisher;

    public record Command(Long restauranteId, Long nuevoPlanId) {}

    @Transactional
    public Suscripcion ejecutar(Command cmd) {
        if (cmd.restauranteId() == null) throw new BusinessException("El restaurante es obligatorio");
        if (cmd.nuevoPlanId() == null) throw new BusinessException("El plan es obligatorio");
        if (!restauranteRepository.existsById(cmd.restauranteId())) throw new BusinessException("El restaurante no existe");
        if (!planRepository.existsById(cmd.nuevoPlanId())) throw new BusinessException("El plan no existe");

        var opt = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(cmd.restauranteId());
        if (opt.isEmpty()) throw new BusinessException("No se encontró suscripción para el restaurante");
        var actual = opt.get();
        if (actual.getEstado() != EstadoSuscripcion.ACTIVA) throw new BusinessException("La suscripción no está activa");

        Long viejoPlanId = actual.getPlanId();
        actual.setEstado(EstadoSuscripcion.CANCELADA);
        suscripcionRepository.save(actual);

        LocalDate fechaInicio = LocalDate.now();
        LocalDate fechaFin = fechaInicio.plusDays(30);
        Suscripcion nueva = Suscripcion.crear(cmd.restauranteId(), cmd.nuevoPlanId(), fechaInicio, fechaFin);
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(nueva));

        eventPublisher.publish(new PlanCambiadoEvent(cmd.restauranteId(), viejoPlanId, cmd.nuevoPlanId()));

        return suscripcionMapper.toDomain(saved);
    }
}
