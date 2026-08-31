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
public class UsuarioRestaurante {
    private Long usuario_id;
    private Long restaurante_id;
    private Long rol_restaurante_id;
    private EstadoUsuario estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static UsuarioRestaurante crear(Long usuario_id, Long restaurante_id, Long rol_restaurante_id) {
        if (usuario_id == null) throw new BusinessException("El usuario_id es obligatorio");
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        if (rol_restaurante_id == null) throw new BusinessException("El rol es obligatorio");
        return UsuarioRestaurante.builder().usuario_id(usuario_id).restaurante_id(restaurante_id).rol_restaurante_id(rol_restaurante_id).estado(EstadoUsuario.ACTIVO).build();
    }
}
