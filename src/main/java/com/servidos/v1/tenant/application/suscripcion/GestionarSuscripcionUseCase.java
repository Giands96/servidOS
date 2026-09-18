package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.PlanJpaRepository;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public Suscripcion suscribir(Long restauranteId, Long planId) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        if (planId == null) throw new BusinessException("El plan es obligatorio");
        if (!restauranteRepository.existsById(restauranteId)) throw new BusinessException("El restaurante no existe");
        if (!planRepository.existsById(planId)) throw new BusinessException("El plan no existe");

        var existente = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(restauranteId);
        if (existente.isPresent()) {
            var e = existente.get();
            if (e.getEstado() == EstadoSuscripcion.ACTIVA && e.getFechaFin() != null && !e.getFechaFin().isBefore(LocalDate.now())) {
                throw new BusinessException("El restaurante ya tiene una suscripción activa");
            }
        }

        Suscripcion s = Suscripcion.crear(restauranteId, planId, LocalDate.now(), LocalDate.now().plusDays(15));
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(s));
        return suscripcionMapper.toDomain(saved);
    }

    @Transactional
    public void cancelar(Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        var opt = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(restauranteId);
        if (opt.isEmpty()) throw new BusinessException("No se encontró suscripción para el restaurante");
        var entity = opt.get();
        if (entity.getEstado() != EstadoSuscripcion.ACTIVA) throw new BusinessException("La suscripción no está activa");
        entity.setEstado(EstadoSuscripcion.CANCELADA);
        suscripcionRepository.save(entity);
    }

    @Transactional
    public Suscripcion renovar(Long restauranteId, Integer duracionDias) {
        if (restauranteId == null) throw new BusinessException("El restaurante es obligatorio");
        if (duracionDias == null || duracionDias <= 0) throw new BusinessException("La duración es obligatoria");
        var opt = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(restauranteId);
        if (opt.isEmpty()) throw new BusinessException("No se encontró suscripción para el restaurante");
        var actual = opt.get();
        if (actual.getEstado() != EstadoSuscripcion.ACTIVA) throw new BusinessException("La suscripción no está activa");

        LocalDate today = LocalDate.now();
        LocalDate fechaInicio;
        if (actual.getFechaFin() != null && !actual.getFechaFin().isBefore(today)) {
            fechaInicio = actual.getFechaFin().plusDays(1);
        } else {
            fechaInicio = today;
        }
        LocalDate fechaFin = fechaInicio.plusDays(duracionDias);

        Suscripcion nueva = Suscripcion.crear(restauranteId, actual.getPlanId(), fechaInicio, fechaFin);
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(nueva));
        return suscripcionMapper.toDomain(saved);
    }
}
