package com.servidos.v1.tenant.api.dto;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import jakarta.validation.constraints.NotNull;

/**
 * Cambio de estado de un restaurante (palanca de acceso del tenant).
 *
 * @param estado {@code INACTIVO} suspende: el cliente conserva lectura de sus datos
 *               pero toda escritura devuelve 402. {@code ACTIVO} reactiva.
 */
public record CambiarEstadoRestauranteRequest(@NotNull EstadoRestaurante estado) {}