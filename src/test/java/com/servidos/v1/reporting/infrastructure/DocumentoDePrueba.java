package com.servidos.v1.reporting.infrastructure;

import com.servidos.v1.reporting.application.export.Columna;
import com.servidos.v1.reporting.application.export.DocumentoReporte;
import com.servidos.v1.reporting.application.export.TipoColumna;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Documento de ejemplo para los tests de los exportadores. */
final class DocumentoDePrueba {

    private DocumentoDePrueba() {}

    static final List<Columna> COLUMNAS = List.of(
            new Columna("N° pedido", TipoColumna.ENTERO),
            new Columna("Fecha de pago", TipoColumna.FECHA_HORA),
            new Columna("Producto", TipoColumna.TEXTO),
            new Columna("Total", TipoColumna.MONEDA));

    static final List<String> ENCABEZADO = List.of(
            "Chifa Central",
            "Semanal · 19/10/2026 – 25/10/2026",
            "Ventas: 2",
            "Total vendido: S/ 1,284.50");

    static DocumentoReporte conDosFilas() {
        return new DocumentoReporte("Reporte de ventas", ENCABEZADO, COLUMNAS, List.of(
                List.of(15L, LocalDateTime.of(2026, 10, 23, 13, 5), "Ceviche mixto", new BigDecimal("1234.50")),
                List.of(14L, LocalDateTime.of(2026, 10, 22, 20, 45), "Ñoquis", new BigDecimal("50.00"))));
    }

    static DocumentoReporte conFilas(int cantidad) {
        List<List<Object>> filas = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            filas.add(List.of((long) i, LocalDateTime.of(2026, 10, 23, 13, 5), "Producto " + i,
                    new BigDecimal("10.00")));
        }
        return new DocumentoReporte("Reporte de ventas", ENCABEZADO, COLUMNAS, filas);
    }
}
