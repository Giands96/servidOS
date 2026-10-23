package com.servidos.v1.ordering.application.mesa;

public record ActualizarMesaCommand(Long mesaId, Integer numero, Integer capacidad) {}
