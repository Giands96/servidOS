package com.servidos.v1.tenant.api.dto;

import com.servidos.v1.tenant.domain.EstadoRestaurante;

public record RestauranteCreadoResponse(Long restauranteId, String slug, String nombre, String direccion,
                                        EstadoRestaurante estado, AdministradorResumenResponse administrador) {}
