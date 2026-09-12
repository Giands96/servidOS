package com.servidos.v1.identity.api.dto;

public record UsuarioResponse(
        Long usuarioId,
        String email,
        String nombre,
        Long restauranteId,
        Long rolRestauranteId) {
}
