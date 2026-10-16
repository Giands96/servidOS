package com.servidos.v1.tenant.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** fecha_fin es inclusiva: un período no puede regalar un día ni correr la fecha de cobro. */
class SuscripcionPeriodoTest {

    @Test
    void unMesTerminaElDiaAnteriorAlMismoNumero() {
        assertEquals(LocalDate.of(2026, 11, 10), Suscripcion.finDeMes(LocalDate.of(2026, 10, 11)));
    }

    /** Doce renovaciones encadenadas siguen cobrando el mismo día del mes. */
    @Test
    void renovacionesEncadenadasNoCorrenLaFechaDeCobro() {
        LocalDate inicio = LocalDate.of(2026, 10, 11);
        for (int i = 0; i < 12; i++) {
            inicio = Suscripcion.finDeMes(inicio).plusDays(1);
        }
        assertEquals(LocalDate.of(2027, 10, 11), inicio);
    }

    @Test
    void demoDeSieteDiasDaSieteDiasDeAcceso() {
        LocalDate hoy = LocalDate.of(2026, 10, 11);
        LocalDate fin = Suscripcion.finDeDias(hoy, 7);
        assertEquals(7, ChronoUnit.DAYS.between(hoy, fin) + 1);
    }
}
