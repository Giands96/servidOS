package com.servidos.v1.identity.api.dto;

import com.servidos.v1.tenant.domain.EstadoRestaurante;

public record MeResponse(
        Long usuarioId,
        String email,
        String nombre,
        Long restauranteId,
        String rol,
        EstadoRestaurante restauranteEstado,
        ResumenSuscripcion suscripcion) {
}
