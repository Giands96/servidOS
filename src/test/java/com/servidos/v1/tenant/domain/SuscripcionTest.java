package com.servidos.v1.tenant.domain;

import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuscripcionTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 10, 5);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 4);

    @Test
    void crearGuardaElMontoYLaMoneda() {
        var s = Suscripcion.crear(1L, 2L, new BigDecimal("34.90"), "PEN", INICIO, FIN);

        assertEquals(0, new BigDecimal("34.90").compareTo(s.getMonto()));
        assertEquals("PEN", s.getMoneda());
        assertEquals(Suscripcion.EstadoSuscripcion.ACTIVA, s.getEstado());
    }

    @Test
    void montoEsObligatorio() {
        var ex = assertThrows(BusinessException.class, () ->
                Suscripcion.crear(1L, 2L, null, "PEN", INICIO, FIN));
        assertEquals("El monto es obligatorio", ex.getMessage());
    }

    @Test
    void montoNegativoSeRechaza() {
        assertThrows(BusinessException.class, () ->
                Suscripcion.crear(1L, 2L, new BigDecimal("-1.00"), "PEN", INICIO, FIN));
    }

    @Test
    void montoCeroEsValido() {
        var s = Suscripcion.crear(1L, 2L, BigDecimal.ZERO, "PEN", INICIO, FIN);
        assertEquals(0, BigDecimal.ZERO.compareTo(s.getMonto()));
    }

    @Test
    void monedaEsObligatoria() {
        var ex = assertThrows(BusinessException.class, () ->
                Suscripcion.crear(1L, 2L, new BigDecimal("34.90"), "  ", INICIO, FIN));
        assertEquals("La moneda es obligatoria", ex.getMessage());
    }

    @Test
    void planEsObligatorio() {
        assertThrows(BusinessException.class, () ->
                Suscripcion.crear(1L, null, new BigDecimal("34.90"), "PEN", INICIO, FIN));
    }
}