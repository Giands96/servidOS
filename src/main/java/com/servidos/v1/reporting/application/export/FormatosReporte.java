package com.servidos.v1.reporting.application.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Cómo se escriben importes y fechas en los reportes (independiente del idioma del servidor). */
public final class FormatosReporte {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatosReporte() {}

    /** {@code S/ 1,234.50}; sin importe, {@code S/ 0.00}. */
    public static String moneda(BigDecimal importe) {
        return String.format(Locale.US, "S/ %,.2f", importe == null ? BigDecimal.ZERO : importe);
    }

    public static String fecha(LocalDate fecha) {
        return FECHA.format(fecha);
    }

    public static String fechaHora(LocalDateTime fechaHora) {
        return FECHA_HORA.format(fechaHora);
    }
}
