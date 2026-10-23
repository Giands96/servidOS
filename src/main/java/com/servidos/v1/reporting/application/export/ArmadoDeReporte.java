package com.servidos.v1.reporting.application.export;

import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.shared.exception.BusinessException;

import java.util.List;
import java.util.Locale;

/** Piezas comunes de los dos reportes exportables: encabezado, nombre de archivo y exportador. */
final class ArmadoDeReporte {

    private ArmadoDeReporte() {}

    /** Restaurante, período, cantidad de ventas y total vendido. */
    static List<String> encabezado(String restaurante, Periodo periodo, RangoFechas rango, ResumenVentas resumen) {
        return List.of(
                restaurante,
                etiquetaDelPeriodo(periodo, rango),
                "Ventas: " + resumen.cantidadVentas(),
                "Total vendido: " + FormatosReporte.moneda(resumen.totalVendido()));
    }

    /** {@code Diario · 23/10/2026}, {@code Semanal · 19/10/2026 – 25/10/2026}. */
    static String etiquetaDelPeriodo(Periodo periodo, RangoFechas rango) {
        return switch (periodo) {
            case DIARIO -> "Diario · " + FormatosReporte.fecha(rango.desde());
            case SEMANAL -> "Semanal · " + FormatosReporte.fecha(rango.desde()) + " – "
                    + FormatosReporte.fecha(rango.hasta());
            case MENSUAL -> "Mensual · " + FormatosReporte.fecha(rango.desde()) + " – "
                    + FormatosReporte.fecha(rango.hasta());
        };
    }

    /** {@code reporte-ventas-semanal-2026-10-19.pdf}: período en minúsculas y primer día del rango. */
    static String nombreDeArchivo(String reporte, Periodo periodo, RangoFechas rango, FormatoExport formato) {
        return "reporte-" + reporte + "-" + periodo.name().toLowerCase(Locale.ROOT) + "-" + rango.desde()
                + "." + formato.extension();
    }

    static ExportadorReporte exportador(List<ExportadorReporte> exportadores, FormatoExport formato) {
        return exportadores.stream()
                .filter(e -> e.formato() == formato)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Formato de exportación no soportado: " + formato));
    }
}
