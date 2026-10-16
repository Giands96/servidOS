package com.servidos.v1.identity.application.usuario;

public record RegistrarAdministradorRestauranteCommand(String nombre, String apellido, String email, String password) {

    // El password no debe llegar a logs ni a trazas de excepción.
    @Override
    public String toString() {
        return "RegistrarAdministradorRestauranteCommand[nombre=" + nombre + ", apellido=" + apellido
                + ", email=" + email + ", password=***]";
    }
}
