package com.servidos.v1.identity.application.usuario;

import com.servidos.v1.identity.domain.EstadoUsuario;

/**
 * Filtros opcionales del listado.
 *
 * ERROR CORREGIDO: el restauranteId vivía acá. Si el cliente puede mandar el tenant,
 * puede pedir los usuarios de otro restaurante. El tenant sale siempre del JWT
 * (TenantContext), nunca del request.
 */
public record ObtenerUsuariosCommand(
        String nombre,
        String apellido,
        String email,
        Long rolRestauranteId,
        String rolNombre,
        EstadoUsuario estado
) {
}
