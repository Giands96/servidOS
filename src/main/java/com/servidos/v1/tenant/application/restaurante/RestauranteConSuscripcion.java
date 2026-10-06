package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import java.time.LocalDate;

/**
 * Read-model para el dashboard de plataforma: un restaurante con su
 * suscripción actual (la de mayor id = última creada) y el nombre del plan.
 * Los campos de suscripción son {@code null} si el restaurante no tiene ninguna.
 */
public record RestauranteConSuscripcion(
        Long restauranteId,
        String slug,
        String nombre,
        String direccion,
        EstadoRestaurante estado,
        Long suscripcionId,
        Long planId,
        String nombrePlan,
        EstadoSuscripcion estadoSuscripcion,
        LocalDate fechaInicio,
        LocalDate fechaFin) {}
