package com.servidos.v1.ordering.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OcuparMesaRequest(@NotNull @Positive Integer comensales) {}
