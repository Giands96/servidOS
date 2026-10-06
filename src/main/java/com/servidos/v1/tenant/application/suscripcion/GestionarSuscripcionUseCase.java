package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.tenant.domain.Plan;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GestionarSuscripcionUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final RestauranteJpaRepository restauranteRepository;
    private final PlanJpaRepository planRepository;
    private final SuscripcionMapper suscripcionMapper;
    private final UsuarioJpaRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Suscripcion suscribir(Long restauranteId, Long planId) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        if (planId == null) throw new BusinessException("El plan es obligatorio");
        if (!restauranteRepository.existsById(restauranteId)) throw new BusinessException("El restaurante no existe");
        var plan = planRepository.findByPlanIdAndEstado(planId, Plan.EstadoPlan.ACTIVO.name())
                .orElseThrow(() -> new BusinessException("El plan no existe o no está activo"));

        var existente = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(restauranteId);
        if (existente.isPresent()) {
            var e = existente.get();
            if (e.getEstado() == EstadoSuscripcion.ACTIVA && e.getFechaFin() != null && !e.getFechaFin().isBefore(LocalDate.now())) {
                throw new BusinessException("El restaurante ya tiene una suscripción activa");
            }
        }

        Suscripcion suscripcion = Suscripcion.crear(restauranteId, plan.getPlanId(),
                plan.getPrecioPlan(), Plan.MONEDA_PEN, LocalDate.now(), LocalDate.now().plusDays(30));
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(suscripcion));
        return suscripcionMapper.toDomain(saved);
    }

    @Transactional
    public void cancelar(Long restauranteId, String password) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        if (password == null || password.isEmpty()) throw new BusinessException("La contraseña es obligatoria");
        exigirPassword(password);
        var opt = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(restauranteId);
        if (opt.isEmpty()) throw new BusinessException("No se encontró suscripción para el restaurante");
        var entity = opt.get();
        if (entity.getEstado() != EstadoSuscripcion.ACTIVA) throw new BusinessException("La suscripción no está activa");
        entity.setEstado(EstadoSuscripcion.CANCELADA);
        suscripcionRepository.save(entity);
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
