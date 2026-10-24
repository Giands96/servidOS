package com.servidos.v1.ordering.api.dto;

import com.servidos.v1.ordering.domain.EstadoMesa;

public record MesaResponse(
        Long mesaId,
        Integer numero,
        Integer capacidad,
        Integer comensales,
        EstadoMesa estado,
        Boolean activa) {}
