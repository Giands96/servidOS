package com.servidos.v1.reporting.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Rango de días de un reporte; {@code hasta} es el último día, inclusive. */
public record RangoFechas(LocalDate desde, LocalDate hasta) {

    /** Primer instante del rango (inclusive). */
    public LocalDateTime inicio() {
        return desde.atStartOfDay();
    }

    /** Primer instante fuera del rango: medianoche del día siguiente a {@code hasta}. */
    public LocalDateTime finExclusivo() {
        return hasta.plusDays(1).atStartOfDay();
    }
}
