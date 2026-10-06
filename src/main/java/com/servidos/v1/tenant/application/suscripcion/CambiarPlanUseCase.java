package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.domain.event.PlanCambiadoEvent;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final UsuarioJpaRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SuscripcionConPlan ejecutar(CambiarPlanCommand cmd) {
        if (cmd.restauranteId() == null) throw new BusinessException("El restaurante es obligatorio");
        if (cmd.nuevoPlanId() == null) throw new BusinessException("El plan es obligatorio");
        if (!Boolean.TRUE.equals(cmd.confirmado())) throw new BusinessException("Debes confirmar el cambio de plan");
        if (cmd.password() == null || cmd.password().isEmpty()) throw new BusinessException("La contraseña es obligatoria");
        exigirPassword(cmd.password());
        if (!restauranteRepository.existsById(cmd.restauranteId())) throw new BusinessException("El restaurante no existe");
        var plan = planRepository.findByPlanIdAndEstado(cmd.nuevoPlanId(), Plan.EstadoPlan.ACTIVO.name())
                .orElseThrow(() -> new BusinessException("El plan no existe o no está activo"));

        LocalDate hoy = LocalDate.now();
        var actual = suscripcionRepository
                .findTopByRestauranteIdAndFechaInicioLessThanEqualOrderBySuscripcionIdDesc(cmd.restauranteId(), hoy)
                .orElseThrow(() -> new BusinessException("No se encontró suscripción para el restaurante"));
        if (actual.getEstado() != EstadoSuscripcion.ACTIVA) throw new BusinessException("La suscripción no está activa");

        // La nueva reemplaza a la actual y a cualquier renovación programada del plan viejo.
        Long viejoPlanId = actual.getPlanId();
        actual.setEstado(EstadoSuscripcion.CANCELADA);
        suscripcionRepository.save(actual);
        for (var programada : suscripcionRepository
                .findByRestauranteIdAndEstadoAndFechaInicioAfter(cmd.restauranteId(), EstadoSuscripcion.ACTIVA, hoy)) {
            programada.setEstado(EstadoSuscripcion.CANCELADA);
            suscripcionRepository.save(programada);
        }

        LocalDate fechaInicio = hoy;
        LocalDate fechaFin = fechaInicio.plusDays(30);
        Suscripcion nueva = Suscripcion.crear(cmd.restauranteId(), plan.getPlanId(),
                plan.getPrecioPlan(), Plan.MONEDA_PEN, fechaInicio, fechaFin);
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(nueva));

        eventPublisher.publish(new PlanCambiadoEvent(cmd.restauranteId(), viejoPlanId, cmd.nuevoPlanId()));

        return new SuscripcionConPlan(suscripcionMapper.toDomain(saved), plan.getNombrePlan());
    }

    private void exigirPassword(String password) {
        Long actorId = CurrentUser.getCurrentUser();
        if (actorId == null) throw new UnauthorizedException("Sesión inválida");
        var usuario = usuarioRepository.findById(actorId)
                .orElseThrow(() -> new UnauthorizedException("Sesión inválida"));
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new UnauthorizedException("Credenciales inválidas");
        }
    }
}
