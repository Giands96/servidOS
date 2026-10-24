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
    /** Baja lógica: una mesa inactiva no se ocupa ni se edita, pero conserva su historial de pedidos. */
    @Builder.Default
    private boolean activa = true;

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
        exigirActiva();
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
        exigirActiva();
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
        exigirActiva();
        if (estado == EstadoMesa.LIBRE) this.estado = EstadoMesa.OCUPADA;
    }

    public void liberar() {
        if (estado == EstadoMesa.LIBRE) throw new BusinessException("La mesa ya está libre");
        this.estado = EstadoMesa.LIBRE;
        this.comensales = null;
    }

    /** Baja lógica: solo una mesa activa y LIBRE; los pedidos abiertos los revisa el caso de uso. */
    public void desactivar() {
        if (!activa) throw new BusinessException("La mesa ya está inactiva");
        if (estado == EstadoMesa.OCUPADA) throw new BusinessException("No se puede dar de baja una mesa ocupada");
        this.activa = false;
    }

    /** Deshace la baja; que el número no lo use otra mesa activa lo revisa el caso de uso. */
    public void reactivar() {
        if (activa) throw new BusinessException("La mesa ya está activa");
        this.activa = true;
    }

    private void exigirActiva() {
        if (!activa) throw new BusinessException("La mesa está inactiva");
    }

    private static void validarNumeroYCapacidad(Integer numero, Integer capacidad) {
        if (numero == null || numero <= 0) throw new BusinessException("El número de mesa debe ser mayor a 0");
        if (capacidad == null || capacidad <= 0) throw new BusinessException("La capacidad debe ser mayor a 0");
    }
}
