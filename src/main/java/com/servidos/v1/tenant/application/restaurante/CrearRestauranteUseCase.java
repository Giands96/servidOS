package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.tenant.domain.Restaurante;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.event.RestauranteCreadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.RestauranteMapper;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CrearRestauranteUseCase {
    private final RestauranteJpaRepository restauranteRepository;
    private final SuscripcionJpaRepository suscripcionRepository;
    private final PlanJpaRepository planRepository;
    private final RestauranteMapper restauranteMapper;
    private final SuscripcionMapper suscripcionMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public Restaurante ejecutar(CrearRestauranteCommand cmd) {
        var plan = validar(cmd);
        Restaurante domain = Restaurante.crear(cmd.slug(), cmd.nombre(), cmd.direccion());
        var savedR = restauranteRepository.save(restauranteMapper.toEntity(domain));
        boolean demo = Boolean.TRUE.equals(cmd.demo());
        int dias = demo ? validarDemoDias(cmd.demoDias()) : 30;
        // monto es lo cobrado: una demo es gratis. El período siguiente lo renueva la
        // plataforma y ahí toma el precio de lista.
        BigDecimal monto = demo ? BigDecimal.ZERO : plan.getPrecioPlan();
        Suscripcion s = Suscripcion.crear(savedR.getRestauranteId(), plan.getPlanId(),
                monto, Plan.MONEDA_PEN, LocalDate.now(), LocalDate.now().plusDays(dias));
        suscripcionRepository.save(suscripcionMapper.toEntity(s));
        eventPublisher.publish(new RestauranteCreadoEvent(savedR.getRestauranteId(), savedR.getSlug()));
        return restauranteMapper.toDomain(savedR);
    }

    private int validarDemoDias(Integer demoDias) {
        if (demoDias == null) throw new BusinessException("Los días de demo son obligatorios");
        if (demoDias < 1 || demoDias > 30) throw new BusinessException("Los días de demo deben estar entre 1 y 30");
        return demoDias;
    }

    private PlanJpaEntity validar(CrearRestauranteCommand cmd) {
        if (cmd.slug() == null || cmd.slug().trim().isEmpty()) throw new BusinessException("El slug es obligatorio");
        if (cmd.nombre() == null || cmd.nombre().trim().isEmpty()) throw new BusinessException("El nombre es obligatorio");
        if (restauranteRepository.existsBySlug(cmd.slug().trim().toLowerCase())) throw new BusinessException("El slug ya existe");
        if (cmd.planId() == null) throw new BusinessException("El plan no existe");
        return planRepository.findByPlanIdAndEstado(cmd.planId(), Plan.EstadoPlan.ACTIVO.name())
                .orElseThrow(() -> new BusinessException("El plan no existe o no está activo"));
    }
}
