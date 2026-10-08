package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.event.RestauranteEstadoCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

/**
 * Renovación de suscripción hecha por la plataforma. El cliente paga fuera del sistema
 * (billetera digital) y la plataforma registra el período pagado: no es self-service.
 * <ul>
 *   <li>Sin suscripción previa crea la primera; el plan es obligatorio.</li>
 *   <li>Con suscripción, el plan es el indicado o el de la última. Si la última sigue
 *       vigente, la nueva se encadena en {@code fecha_fin + 1} (queda programada); si no,
 *       arranca hoy.</li>
 *   <li>Si el restaurante estaba INACTIVO, queda ACTIVO: pagó y sigue su flujo normal.</li>
 * </ul>
 * El monto se copia del precio de lista vigente del plan: cambiar el precio de lista
 * afecta a quien renueva, pero las suscripciones ya emitidas conservan su monto.
 */
@Service
@RequiredArgsConstructor
public class RenovarSuscripcionUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final PlanJpaRepository planRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final SuscripcionMapper suscripcionMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public SuscripcionConPlan ejecutar(RenovarSuscripcionCommand cmd) {
        if (cmd.restauranteId() == null) throw new BusinessException("El restaurante es obligatorio");
        var restaurante = restauranteRepository.findById(cmd.restauranteId())
                .orElseThrow(() -> new BusinessException("El restaurante no existe"));

        var ultima = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(cmd.restauranteId());
        Long planId = cmd.planId() != null ? cmd.planId() : ultima.map(s -> s.getPlanId()).orElse(null);
        if (planId == null) throw new BusinessException("El plan es obligatorio para la primera suscripción");
        var plan = planRepository.findByPlanIdAndEstado(planId, Plan.EstadoPlan.ACTIVO.name())
                .orElseThrow(() -> new BusinessException("El plan no existe o no está activo"));

        LocalDate hoy = LocalDate.now();
        LocalDate fechaInicio = ultima
                .filter(s -> s.getFechaFin() != null && !s.getFechaFin().isBefore(hoy))
                .map(s -> s.getFechaFin().plusDays(1))
                .orElse(hoy);
        LocalDate fechaFin = fechaInicio.plusMonths(1);

        Suscripcion nueva = Suscripcion.crear(cmd.restauranteId(), plan.getPlanId(),
                plan.getPrecioPlan(), Plan.MONEDA_PEN, fechaInicio, fechaFin);
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(nueva));

        if (restaurante.getEstado() == EstadoRestaurante.INACTIVO) {
            restaurante.setEstado(EstadoRestaurante.ACTIVO);
            restauranteRepository.save(restaurante);
            eventPublisher.publish(new RestauranteEstadoCambiadoEvent(
                    cmd.restauranteId(), EstadoRestaurante.INACTIVO, EstadoRestaurante.ACTIVO));
        }
        return new SuscripcionConPlan(suscripcionMapper.toDomain(saved), plan.getNombrePlan());
    }
}
