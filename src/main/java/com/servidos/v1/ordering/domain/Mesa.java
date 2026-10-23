package com.servidos.v1.ordering.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mesa {
    private Long mesa_id;
    private Long restaurante_id;
    private Integer numero;
    private Integer capacidad;
    /** Personas sentadas ahora; null con la mesa LIBRE o si la ocupó un pedido sin indicarlo. */
    private Integer comensales;
    private EstadoMesa estado;

    public static Mesa crear(Long restaurante_id, Integer numero, Integer capacidad) {
        if (restaurante_id == null) throw new BusinessException("El restaurante_id es obligatorio");
        validarNumeroYCapacidad(numero, capacidad);
        return Mesa.builder()
                .restaurante_id(restaurante_id)
                .numero(numero)
                .capacidad(capacidad)
                .estado(EstadoMesa.LIBRE)
                .build();
    }

    public void actualizar(Integer numero, Integer capacidad) {
        validarNumeroYCapacidad(numero, capacidad);
        if (comensales != null && capacidad < comensales) {
            throw new BusinessException(
                    "La capacidad no puede ser menor a los comensales actuales (" + comensales + ")");
        }
        this.numero = numero;
        this.capacidad = capacidad;
    }

    private static void validarNumeroYCapacidad(Integer numero, Integer capacidad) {
        if (numero == null || numero <= 0) throw new BusinessException("El número de mesa debe ser mayor a 0");
        if (capacidad == null || capacidad <= 0) throw new BusinessException("La capacidad debe ser mayor a 0");
    }
}
