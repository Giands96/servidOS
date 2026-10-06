package com.servidos.v1.kitchen.api.dto;

public record CocinaDetalleResponse(
        Long detalleId,
        Long productoId,
        String nombreProducto,
        Integer cantidad,
        String observacion) {}
