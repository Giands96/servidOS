package com.servidos.v1.tenant.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Restaurante {
    private Long restaurante_id;
    private String slug;
    private String nombre;
    private String direccion;
    private EstadoRestaurante estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Restaurante crear(String slug, String nombre, String direccion) {
        if (slug == null || slug.trim().isEmpty()) throw new BusinessException("El slug es obligatorio");
        String s = slug.trim().toLowerCase();
        if (s.length() < 3 || s.length() > 100) throw new BusinessException("El slug debe tener entre 3 y 100 caracteres");
        if (!s.matches("^[a-z0-9-]+$")) throw new BusinessException("El slug solo puede contener letras minúsculas, números y guiones");
        if (nombre == null || nombre.trim().isEmpty()) throw new BusinessException("El nombre es obligatorio");
        if (nombre.trim().length() > 150) throw new BusinessException("El nombre no puede tener más de 150 caracteres");
        return Restaurante.builder().slug(s).nombre(nombre.trim()).direccion(direccion).estado(EstadoRestaurante.ACTIVO).build();
    }
}
