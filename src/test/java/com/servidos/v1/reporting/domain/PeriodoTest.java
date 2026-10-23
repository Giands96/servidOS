package com.servidos.v1.reporting.domain;

import com.servidos.v1.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PeriodoTest {

    private static void assertRango(LocalDate desde, LocalDate hasta, RangoFechas rango) {
        assertEquals(desde, rango.desde());
        assertEquals(hasta, rango.hasta());
    }

    @Test
    void diarioEsElMismoDia() {
        var dia = LocalDate.of(2026, 10, 23);
        assertRango(dia, dia, Periodo.DIARIO.rango(dia));
    }

    @Test
    void semanalDeUnMiercolesVaDeLunesADomingo() {
        assertRango(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                Periodo.SEMANAL.rango(LocalDate.of(2026, 10, 21)));
    }

    @Test
    void semanalDeUnViernesVaDeLunesADomingo() {
        assertRango(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                Periodo.SEMANAL.rango(LocalDate.of(2026, 10, 23)));
    }

    @Test
    void semanalDeUnLunesEmpiezaEseMismoDia() {
        assertRango(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                Periodo.SEMANAL.rango(LocalDate.of(2026, 10, 19)));
    }

    @Test
    void semanalDeUnDomingoTerminaEseMismoDia() {
        assertRango(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                Periodo.SEMANAL.rango(LocalDate.of(2026, 10, 25)));
    }

    @Test
    void semanalCruzaDeMesYDeAnio() {
        assertRango(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3),
                Periodo.SEMANAL.rango(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void mensualDeFebreroBisiesto() {
        assertRango(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29),
                Periodo.MENSUAL.rango(LocalDate.of(2028, 2, 10)));
    }

    @Test
    void mensualDeFebreroComun() {
        assertRango(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28),
                Periodo.MENSUAL.rango(LocalDate.of(2027, 2, 28)));
    }

    @Test
    void mensualDeDiciembre() {
        assertRango(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
                Periodo.MENSUAL.rango(LocalDate.of(2026, 12, 15)));
    }

    @Test
    void elRangoEsSemiabierto() {
        var rango = Periodo.SEMANAL.rango(LocalDate.of(2026, 10, 23));

        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), rango.inicio());
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), rango.finExclusivo());
    }

    @Test
    void elFinExclusivoDeUnMesDeDiciembreEsElPrimeroDeEnero() {
        var rango = Periodo.MENSUAL.rango(LocalDate.of(2026, 12, 31));

        assertEquals(LocalDateTime.of(2026, 12, 1, 0, 0), rango.inicio());
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), rango.finExclusivo());
    }

    @Test
    void fechaNulaEsInvalida() {
        for (var periodo : Periodo.values()) {
            assertThrows(BusinessException.class, () -> periodo.rango(null));
        }
    }
}
