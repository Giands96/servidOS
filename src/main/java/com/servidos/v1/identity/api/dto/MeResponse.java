package com.servidos.v1.identity.api.dto;

public record MeResponse(
        Long usuarioId,
        String email,
        String nombre,
        Long restauranteId,
        String rol) {
}
