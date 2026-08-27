package com.servidos.v1.identity.domain.event;

import com.servidos.v1.shared.event.DomainEvent;

import java.time.Instant;
import java.util.Date;

public record UsuarioCreadoEvent(
        Long usuarioId,
        Long restauranteId,
        Long rolId,
        String email,
        Date fechaCreacion

        // EstadoUsuario estado
) implements DomainEvent {
    @Override
    public Instant ocurredAt() {
        return Instant.now();
    }
}
