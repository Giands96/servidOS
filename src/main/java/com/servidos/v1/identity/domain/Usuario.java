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

    protected void onCreate() {
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now(); // Al crear, ambas fechas son iguales
    }
    protected void onUpdate() {
        this.updated_at = LocalDateTime.now(); // Al editar, solo cambia esta fecha
    }



}
