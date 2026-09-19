package com.servidos.v1.tenant.api.dto;

import jakarta.validation.constraints.NotNull;

public record CambiarPlanRequest(@NotNull Long nuevoPlanId) {}
