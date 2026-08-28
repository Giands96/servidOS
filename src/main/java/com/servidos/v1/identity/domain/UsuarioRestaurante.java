package com.servidos.v1.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRestaurante {
    private Long usuario_id;
    private Long restaurante_id;
    private Long rol_restaurante_id;
    private EstadoUsuario estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
