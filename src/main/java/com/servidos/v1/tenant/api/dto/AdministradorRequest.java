package com.servidos.v1.tenant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dueño (rol ADMINISTRADOR) que se crea junto con el restaurante. */
@Schema(description = "Dueño del restaurante: se crea con rol ADMINISTRADOR junto con el restaurante")
public record AdministradorRequest(
        @Schema(description = "Nombre", example = "Ana", minLength = 2, maxLength = 100)
        @NotBlank @Size(min = 2, max = 100) String nombre,
        @Schema(description = "Apellido", example = "Paz", maxLength = 100)
        @NotBlank @Size(max = 100) String apellido,
        @Schema(description = "Email de acceso; único en toda la plataforma", example = "ana@demo.pe", maxLength = 150)
        @NotBlank @Email @Size(max = 150) String email,
        @Schema(description = "Contraseña inicial", minLength = 8, maxLength = 72, accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password) {

    // El password no debe llegar a logs ni a trazas de excepción.
    @Override
    public String toString() {
        return "AdministradorRequest[nombre=" + nombre + ", apellido=" + apellido
                + ", email=" + email + ", password=***]";
    }
}
