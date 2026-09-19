package com.servidos.v1.tenant.application.suscripcion;

import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.tenant.domain.Suscripcion;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import com.servidos.v1.tenant.infrastructure.mapper.SuscripcionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class RenovarSuscripcionUseCase {
    private final SuscripcionJpaRepository suscripcionRepository;
    private final SuscripcionMapper suscripcionMapper;

    @Transactional
    public Suscripcion ejecutar(RenovarSuscripcionCommand cmd) {
        if (cmd.restauranteId() == null) throw new BusinessException("El restaurante es obligatorio");
        var opt = suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDesc(cmd.restauranteId());
        if (opt.isEmpty()) throw new BusinessException("No se encontró suscripción para el restaurante");
        var actual = opt.get();
        if (actual.getEstado() != EstadoSuscripcion.ACTIVA) throw new BusinessException("La suscripción no está activa");

        LocalDate today = LocalDate.now();
        LocalDate fechaInicio = (actual.getFechaFin() != null && !actual.getFechaFin().isBefore(today))
                ? actual.getFechaFin().plusDays(1)
                : today;
        LocalDate fechaFin = fechaInicio.plusMonths(1);

        Suscripcion nueva = Suscripcion.crear(cmd.restauranteId(), actual.getPlanId(), fechaInicio, fechaFin);
        var saved = suscripcionRepository.save(suscripcionMapper.toEntity(nueva));
        return suscripcionMapper.toDomain(saved);
    }
}
