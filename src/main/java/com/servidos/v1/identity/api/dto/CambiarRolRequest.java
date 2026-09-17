package com.servidos.v1.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nuevo rol a otorgar. En sesión de restaurante es un rol_restaurante; en sesión de plataforma, un rol_plataforma.")
public record CambiarRolRequest(
        @Schema(description = "ID del rol destino (debe existir y estar ACTIVO)", example = "20")
        @NotNull Long nuevoRolId) {
}
