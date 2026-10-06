package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RenovarSuscripcionUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final PlanJpaRepository planRepository;
    private final SuscripcionMapper suscripcionMapper;
    private final UsuarioJpaRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SuscripcionConPlan ejecutar(RenovarSuscripcionCommand cmd) {
        if (cmd.restauranteId() == null) throw new BusinessException("El restaurante es obligatorio");
        if (cmd.password() == null || cmd.password().isEmpty()) throw new BusinessException("La contraseña es obligatoria");
        exigirPassword(cmd.password());
        var opt = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(cmd.restauranteId());
        if (opt.isEmpty()) throw new BusinessException("No se encontró suscripción para el restaurante");
        var actual = opt.get();
        // Una CANCELADA también puede renovar: es la vía de reactivación self-service.
        // Crea una ACTIVA nueva por INSERT (la vieja queda como historial); si la fecha
        // de fin ya pasó, arranca hoy por la regla de abajo.

        // El monto se vuelve a copiar del precio de lista vigente del plan, no del
        // monto de la suscripción anterior: la decisión comercial es renovar a precio
        // actual, así que subir el precio de lista sí cambia lo que paga quien renueva.
        // Las suscripciones ya emitidas conservan su monto histórico.
        var plan = planRepository.findByPlanIdAndEstado(actual.getPlanId(), Plan.EstadoPlan.ACTIVO.name())
                .orElseThrow(() -> new BusinessException("El plan del restaurante no está activo, no se puede renovar"));

        LocalDate today = LocalDate.now();
        LocalDate fechaInicio = (actual.getFechaFin() != null && !actual.getFechaFin().isBefore(today))
                ? actual.getFechaFin().plusDays(1)
                : today;
        LocalDate fechaFin = fechaInicio.plusMonths(1);

        Suscripcion nueva = Suscripcion.crear(cmd.restauranteId(), plan.getPlanId(),
                plan.getPrecioPlan(), Plan.MONEDA_PEN, fechaInicio, fechaFin);
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(nueva));
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
