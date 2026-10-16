package com.servidos.v1.identity.application.usuario;

/** Dueño de un restaurante nuevo. El password viaja en claro hasta hashearse: nunca se imprime. */
public record CrearAdministradorCommand(String nombre, String apellido, String email, String password) {
    @Override
    public String toString() {
        return "CrearAdministradorCommand[nombre=" + nombre + ", apellido=" + apellido
                + ", email=" + email + ", password=***]";
    }
}
