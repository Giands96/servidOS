package com.servidos.v1.catalog.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Producto {

    private Long producto_id;
    private Long restaurante_id;
    private Long categoria_id;
    private String nombre;
    private String descripcion;
    private String imagen_url;
    private BigDecimal precio;
    private EstadoProducto estado;
    private Integer tiempo_preparacion;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Producto crear(Long restaurante_id,
                                 Long categoria_id,
                                 String nombre,
                                 String descripcion,
                                 String imagen_url,
                                 BigDecimal precio,
                                 EstadoProducto estado,
                                 Integer tiempo_preparacion) {

        /*
        *   Validación multitenancy = El restaurante_id es obligatorio.
        * */

        if(restaurante_id == null) {
            throw new BusinessException("El restaurante_id es obligatorio");
        }

        /*
        *   Validación 1 = El nombre del producto no puede ser nulo o vacío.
        *
        * */

        if(nombre == null || nombre.trim().isEmpty()) {
            throw new BusinessException("El nombre del producto es obligatorio");
        } else if(nombre.trim().length() > 150) {
            throw new BusinessException("El nombre del producto no puede tener más de 150 caracteres");
        }
        /*
        *
        *   Validación 2 = El precio del producto debe ser mayor a 0.
        *
        */
        if(precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El precio debe ser mayor a 0");
        }

        /*
        *
        *   Validación 3 = El tiempo de preparación del producto debe ser un número positivo.
        *
        */
        if(tiempo_preparacion != null && tiempo_preparacion <= 0) {
            throw new BusinessException("El tiempo de preparación debe ser un número positivo");
        }


        return Producto.builder()
                .restaurante_id(restaurante_id)
                .categoria_id(categoria_id)
                .nombre(nombre.trim())
                .descripcion(descripcion)
                .imagen_url(imagen_url)
                .precio(precio)
                .estado(estado != null ? estado : EstadoProducto.DISPONIBLE)
                .tiempo_preparacion(tiempo_preparacion)
                .build();
    }

    public static Producto actualizar(Long producto_id,
                                 Long restaurante_id,
                                 Long categoria_id,
                                 String nombre,
                                 String descripcion,
                                 String imagen_url,
                                 BigDecimal precio,
                                 EstadoProducto estado,
                                 Integer tiempo_preparacion) {

        if(producto_id == null) {
            throw new BusinessException("El producto_id es obligatorio");
        }

        if(restaurante_id == null) {
            throw new BusinessException("El restaurante_id es obligatorio");
        }

        if(nombre == null || nombre.trim().isEmpty()) {
            throw new BusinessException("El nombre del producto es obligatorio");
        } else if(nombre.trim().length() > 150) {
            throw new BusinessException("El nombre del producto no puede tener más de 150 caracteres");
        }

        if(precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("El precio debe ser mayor a 0");
        }

        if(tiempo_preparacion != null && tiempo_preparacion <= 0) {
            throw new BusinessException("El tiempo de preparación debe ser un número positivo");
        }

        return Producto.builder()
                .producto_id(producto_id)
                .restaurante_id(restaurante_id)
                .categoria_id(categoria_id)
                .nombre(nombre.trim())
                .descripcion(descripcion)
                .imagen_url(imagen_url)
                .precio(precio)
                .estado(estado != null ? estado : EstadoProducto.DISPONIBLE)
                .tiempo_preparacion(tiempo_preparacion)
                .build();
    }
}
