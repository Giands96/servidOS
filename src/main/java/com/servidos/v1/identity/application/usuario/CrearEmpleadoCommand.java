package com.servidos.v1.identity.application.usuario;

public record CrearEmpleadoCommand(
        String nombre,
        String apellido,
        String email,
        String password,
        Long rolRestauranteId,
        Long restauranteId
) {}
