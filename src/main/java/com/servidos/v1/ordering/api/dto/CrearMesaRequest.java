package com.servidos.v1.ordering.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrearMesaRequest(
        @NotNull @Positive Integer numero,
        @NotNull @Positive Integer capacidad) {}
