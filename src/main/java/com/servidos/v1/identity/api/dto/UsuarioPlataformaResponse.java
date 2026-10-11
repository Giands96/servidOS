package com.servidos.v1.identity.api.dto;

public record UsuarioPlataformaResponse(
        Long usuarioId,
        String email,
        String nombre,
        Long rolPlataformaId) {
}
