package com.servidos.v1.ordering.domain;

import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MesaTest {

    @Test
    void crearDejaLaMesaLibreYSinComensales() {
        var mesa = Mesa.crear(10L, 5, 4);

        assertEquals(10L, mesa.getRestaurante_id());
        assertEquals(5, mesa.getNumero());
        assertEquals(4, mesa.getCapacidad());
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getComensales());
    }

    @Test
    void crearSinRestauranteFalla() {
        assertThrows(BusinessException.class, () -> Mesa.crear(null, 5, 4));
    }

    @Test
    void crearExigeNumeroMayorACero() {
        assertThrows(BusinessException.class, () -> Mesa.crear(10L, null, 4));
        assertThrows(BusinessException.class, () -> Mesa.crear(10L, 0, 4));
        assertThrows(BusinessException.class, () -> Mesa.crear(10L, -1, 4));
    }

    @Test
    void crearExigeCapacidadMayorACero() {
        assertThrows(BusinessException.class, () -> Mesa.crear(10L, 5, null));
        assertThrows(BusinessException.class, () -> Mesa.crear(10L, 5, 0));
    }

    @Test
    void actualizarCambiaNumeroYCapacidad() {
        var mesa = Mesa.crear(10L, 5, 4);

        mesa.actualizar(7, 6);

        assertEquals(7, mesa.getNumero());
        assertEquals(6, mesa.getCapacidad());
    }

    @Test
    void actualizarValidaLosMismosLimitesQueCrear() {
        var mesa = Mesa.crear(10L, 5, 4);

        assertThrows(BusinessException.class, () -> mesa.actualizar(0, 4));
        assertThrows(BusinessException.class, () -> mesa.actualizar(5, 0));
        assertEquals(5, mesa.getNumero());
        assertEquals(4, mesa.getCapacidad());
    }

    @Test
    void ocuparMarcaOcupadaConLosComensales() {
        var mesa = Mesa.crear(10L, 5, 4);

        mesa.ocupar(3);

        assertEquals(EstadoMesa.OCUPADA, mesa.getEstado());
        assertEquals(3, mesa.getComensales());
    }

    @Test
    void ocuparAdmiteDesdeUnoHastaLaCapacidad() {
        assertEquals(1, ocupada(1).getComensales());
        assertEquals(4, ocupada(4).getComensales());
    }

    private static Mesa ocupada(int comensales) {
        var mesa = Mesa.crear(10L, 5, 4);
        mesa.ocupar(comensales);
        return mesa;
    }

    @Test
    void ocuparRechazaComensalesFueraDeRango() {
        var mesa = Mesa.crear(10L, 5, 4);

        var ex = assertThrows(BusinessException.class, () -> mesa.ocupar(5));

        assertEquals("Los comensales deben estar entre 1 y 4", ex.getMessage());
        assertThrows(BusinessException.class, () -> mesa.ocupar(0));
        assertThrows(BusinessException.class, () -> mesa.ocupar(null));
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getComensales());
    }

    @Test
    void ocuparUnaMesaOcupadaFalla() {
        var mesa = ocupada(2);

        var ex = assertThrows(BusinessException.class, () -> mesa.ocupar(3));

        assertEquals("La mesa ya está ocupada", ex.getMessage());
        assertEquals(2, mesa.getComensales());
    }

    @Test
    void liberarDejaLaMesaLibreYSinComensales() {
        var mesa = ocupada(3);

        mesa.liberar();

        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getComensales());
    }

    @Test
    void liberarUnaMesaLibreFalla() {
        var mesa = Mesa.crear(10L, 5, 4);

        var ex = assertThrows(BusinessException.class, mesa::liberar);

        assertEquals("La mesa ya está libre", ex.getMessage());
    }

    @Test
    void actualizarNoPermiteCapacidadMenorALosComensalesActuales() {
        var mesa = Mesa.builder().restaurante_id(10L).numero(5).capacidad(6).comensales(5)
                .estado(EstadoMesa.OCUPADA).build();

        var ex = assertThrows(BusinessException.class, () -> mesa.actualizar(5, 4));

        assertEquals("La capacidad no puede ser menor a los comensales actuales (5)", ex.getMessage());
        assertEquals(6, mesa.getCapacidad());
    }
}
