package com.servidos.v1.catalog.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Categoria {

    private Long categoria_id;
    private Long restaurante_id;
    private String nombre;
    private EstadoCategoria estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Categoria crear(Long restaurante_id, String nombre, EstadoCategoria estado) {
        if (restaurante_id == null) {
            throw new BusinessException("El restaurante_id es obligatorio");
        }
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new BusinessException("El nombre de la categoría es obligatorio");
        }
        if (estado == null) {
            throw new BusinessException("El estado de la categoría es obligatorio");
        }
        return Categoria.builder()
                .restaurante_id(restaurante_id)
                .nombre(nombre)
                .estado(estado)
                .build();
    }

}
