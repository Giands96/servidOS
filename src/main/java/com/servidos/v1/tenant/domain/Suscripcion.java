package com.servidos.v1.tenant.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Suscripcion {

    private Long suscripcion_id;
    private Long restaurante_id;
    private Long plan_id;
    private BigDecimal monto;
    private String moneda;
    private EstadoSuscripcion estado;
    private LocalDate fecha_inicio;
    private LocalDate fecha_fin;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public enum EstadoSuscripcion {
        ACTIVA,
        CANCELADA
    }

    public static Suscripcion crear(Long restaurante_id, Long plan_id, BigDecimal monto, String moneda,
                                    LocalDate fecha_inicio, LocalDate fecha_fin) {
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (plan_id == null) throw new BusinessException("El plan_id es obligatorio");
        if (monto == null) throw new BusinessException("El monto es obligatorio");
        if (monto.signum() < 0) throw new BusinessException("El monto no puede ser negativo");
        if (moneda == null || moneda.isBlank()) throw new BusinessException("La moneda es obligatoria");
        if (fecha_inicio == null) throw new BusinessException("La fecha de inicio es obligatoria");
        if (fecha_fin == null || !fecha_fin.isAfter(fecha_inicio)) throw new BusinessException("La fecha de fin debe ser posterior a la fecha de inicio");
        return Suscripcion.builder().restaurante_id(restaurante_id).plan_id(plan_id).monto(monto).moneda(moneda)
                .estado(EstadoSuscripcion.ACTIVA).fecha_inicio(fecha_inicio).fecha_fin(fecha_fin).build();
    }
}
