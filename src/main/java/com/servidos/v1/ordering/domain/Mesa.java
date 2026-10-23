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

    /** Sienta a una mesa LIBRE; {@code comensales} va de 1 a la capacidad de la mesa. */
    public void ocupar(Integer comensales) {
        if (estado == EstadoMesa.OCUPADA) throw new BusinessException("La mesa ya está ocupada");
        if (comensales == null || comensales < 1 || comensales > capacidad) {
            throw new BusinessException("Los comensales deben estar entre 1 y " + capacidad);
        }
        this.estado = EstadoMesa.OCUPADA;
        this.comensales = comensales;
    }

    /**
     * Un pedido de mesa sienta a la mesa libre sin saber cuántos son; con la mesa ya ocupada
     * (otro pedido del mismo grupo, o {@link #ocupar}) no cambia nada.
     */
    public void ocuparPorPedido() {
        if (estado == EstadoMesa.LIBRE) this.estado = EstadoMesa.OCUPADA;
    }

    public void liberar() {
        if (estado == EstadoMesa.LIBRE) throw new BusinessException("La mesa ya está libre");
        this.estado = EstadoMesa.LIBRE;
        this.comensales = null;
    }

    private static void validarNumeroYCapacidad(Integer numero, Integer capacidad) {
        if (numero == null || numero <= 0) throw new BusinessException("El número de mesa debe ser mayor a 0");
        if (capacidad == null || capacidad <= 0) throw new BusinessException("La capacidad debe ser mayor a 0");
    }
}
