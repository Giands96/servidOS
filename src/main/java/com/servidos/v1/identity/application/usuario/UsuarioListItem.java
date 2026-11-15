package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.domain.RolRestaurante;

import java.time.LocalDateTime;

public record UsuarioListItem(
        Long usuarioId,
        String nombre,
        String apellido,
        String email,
        Long rolRestauranteId,
        String rolNombre,
        EstadoUsuario estado,
        LocalDateTime ultimoAcceso
) {
}
