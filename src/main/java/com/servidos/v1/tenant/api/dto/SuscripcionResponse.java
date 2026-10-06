package com.servidos.v1.tenant.api.dto;

import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Suscripción del restaurante.
 *
 * @param planId     id del plan (referencia)
 * @param nombrePlan nombre legible del plan; viene resuelto por JOIN para que el
 *                   frontend pueda mostrar "Estás en Estándar" sin un segundo request
 * @param monto      snapshot de lo que se cobró en esta suscripción. Es histórico a
 *                   propósito: cambiar el precio de lista del plan NO altera este valor
 * @param moneda     ISO 4217 del monto (hoy solo PEN)
 */
public record SuscripcionResponse(Long suscripcionId, Long restauranteId, Long planId, String nombrePlan,
                                  BigDecimal monto, String moneda, EstadoSuscripcion estado,
                                  LocalDate fechaInicio, LocalDate fechaFin) {}