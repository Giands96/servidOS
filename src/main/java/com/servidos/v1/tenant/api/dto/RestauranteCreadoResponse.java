package com.servidos.v1.tenant.api.dto;

import com.servidos.v1.tenant.domain.EstadoRestaurante;

/** Respuesta del alta: el restaurante y el dueño creado (nunca el password). */
public record RestauranteCreadoResponse(Long restauranteId, String slug, String nombre, String direccion,
                                        EstadoRestaurante estado, AdministradorCreado administrador) {

    public record AdministradorCreado(Long usuarioId, String email, String nombre) {}
}
