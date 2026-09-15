package com.servidos.v1.identity.application.auth;

public record SesionRenovada(String refreshToken, Long usuarioId, Long restauranteId, String rol) {}
