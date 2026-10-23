package com.servidos.v1.reporting.application.export;

import java.util.List;

/**
 * Reporte listo para dibujar, independiente del formato: título, líneas de encabezado y una tabla.
 * Cada fila trae un valor por columna, del tipo que indica {@link TipoColumna} (null = celda vacía).
 */
public record DocumentoReporte(String titulo, List<String> encabezado, List<Columna> columnas,
                               List<List<Object>> filas) {}
