package com.servidos.v1.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {
    Long usuario_id;
    String nombre;
    String apellido;
    String email;
    String password_hash;
    LocalDateTime ultimo_acceso;
    EstadoUsuario estado;
    LocalDateTime created_at;
    LocalDateTime updated_at;
}
