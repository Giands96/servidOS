package com.servidos.v1.reporting.application.export;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatosReporteTest {

    @Test
    void monedaConSeparadorDeMilesYDosDecimales() {
        assertEquals("S/ 1,234.50", FormatosReporte.moneda(new BigDecimal("1234.5")));
        assertEquals("S/ 0.00", FormatosReporte.moneda(BigDecimal.ZERO));
        assertEquals("S/ 1,000,000.00", FormatosReporte.moneda(new BigDecimal("1000000")));
    }

    @Test
    void monedaSinImporteEsCero() {
        assertEquals("S/ 0.00", FormatosReporte.moneda(null));
    }

    @Test
    void fechaYFechaHora() {
        assertEquals("23/10/2026", FormatosReporte.fecha(LocalDate.of(2026, 10, 23)));
        assertEquals("05/01/2027 09:07", FormatosReporte.fechaHora(LocalDateTime.of(2027, 1, 5, 9, 7, 55)));
    }
}
