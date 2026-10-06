package com.servidos.v1.tenant.api.dto;

import java.math.BigDecimal;

/**
 * Plan del catálogo.
 *
 * @param precio precio de lista por defecto. La suscripción NO lo hereda: copia este
 *               valor a {@code suscripcion.monto} al contratar, así que cambiar este
 *               campo solo afecta a las suscripciones que se creen o renueven después
 * @param moneda ISO 4217 del precio (hoy solo PEN)
 */
public record PlanResponse(Long planId, String nombre, BigDecimal precio, String descripcion,
                           String estado, String moneda) {}