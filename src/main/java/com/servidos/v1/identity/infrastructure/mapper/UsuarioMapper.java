package com.servidos.v1.identity.infrastructure.mapper;

import com.servidos.v1.identity.domain.Usuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public Usuario toDomain(UsuarioJpaEntity e) {
        if (e == null) {
            return null;
        }
        return Usuario.builder()
                .usuario_id(e.getUsuarioId())
                .nombre(e.getNombre())
                .apellido(e.getApellido())
                .email(e.getEmail())
                .password_hash(e.getPasswordHash())
                .ultimo_acceso(e.getUltimoAcceso())
                .estado(e.getEstado())
                .created_at(e.getCreatedAt())
                .updated_at(e.getUpdatedAt())
                .build();
    }

    public UsuarioJpaEntity toEntity(Usuario d) {
        if (d == null) {
            return null;
        }
        UsuarioJpaEntity e = new UsuarioJpaEntity();
        e.setUsuarioId(d.getUsuario_id());
        e.setNombre(d.getNombre());
        e.setApellido(d.getApellido());
        e.setEmail(d.getEmail());
        e.setPasswordHash(d.getPassword_hash());
        e.setUltimoAcceso(d.getUltimo_acceso());
        e.setEstado(d.getEstado());
        e.setCreatedAt(d.getCreated_at());
        e.setUpdatedAt(d.getUpdated_at());
        return e;
    }
}
