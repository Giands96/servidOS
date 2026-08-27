package com.servidos.v1.identity.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UsuarioPlataforma {
    Long usuario_id;
    Long rol_plataforma_id;
    EstadoUsuario estado;
    LocalDateTime created_at;
    LocalDateTime updated_at;
}
