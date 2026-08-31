package com.servidos.v1.identity.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
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

    public static Usuario crear(String nombre, String apellido, String email, String password_hash) {
        if (nombre == null || nombre.trim().isEmpty() || nombre.trim().length() < 2) throw new BusinessException("El nombre es obligatorio");
        if (email == null || email.trim().isEmpty() || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) throw new BusinessException("El email no es válido");
        if (password_hash == null || password_hash.isEmpty()) throw new BusinessException("El password es obligatorio");
        return Usuario.builder().nombre(nombre.trim()).apellido(apellido).email(email.trim().toLowerCase()).password_hash(password_hash).estado(EstadoUsuario.ACTIVO).build();
    }
}
