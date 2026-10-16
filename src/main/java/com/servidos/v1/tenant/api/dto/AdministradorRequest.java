package com.servidos.v1.tenant.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dueño del restaurante. Largos máximos iguales a las columnas de {@code usuario}. */
public record AdministradorRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    @Override
    public String toString() {
        return "AdministradorRequest[nombre=" + nombre + ", apellido=" + apellido + ", email=" + email + ", password=***]";
    }
}
