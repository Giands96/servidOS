package com.servidos.v1.tenant.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearRestauranteRequest(
        @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "^[a-z0-9-]+$") String slug,
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 255) String direccion,
        @NotNull Long planId,
        Boolean demo,
        @Min(1) @Max(30) Integer demoDias) {}
