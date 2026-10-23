package com.servidos.v1.reporting.infrastructure;

import com.servidos.v1.reporting.application.export.DocumentoReporte;
import com.servidos.v1.reporting.application.export.ExportadorReporte;
import com.servidos.v1.reporting.application.export.FormatoExport;
import com.servidos.v1.reporting.application.export.TipoColumna;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * xlsx de una hoja: título, líneas de encabezado, una fila en blanco y la tabla. Los números van como
 * números (con formato) y las fechas como fechas, para que se puedan sumar y ordenar en Excel.
 */
@Component
public class XlsxExportadorReporte implements ExportadorReporte {

    static final String FORMATO_MONEDA = "#,##0.00";
    static final String FORMATO_FECHA_HORA = "dd/MM/yyyy HH:mm";
    private static final int MAX_NOMBRE_HOJA = 31;

    @Override
    public FormatoExport formato() {
        return FormatoExport.XLSX;
    }

    @Override
    public byte[] exportar(DocumentoReporte documento) {
        var salida = new ByteArrayOutputStream();
        try (var libro = new Workbook(salida, "ServidOS", "1.0")) {
            var hoja = libro.newWorksheet(nombreDeHoja(documento.titulo()));

            int fila = 0;
            hoja.value(fila, 0, documento.titulo());
            hoja.style(fila, 0).bold().fontSize(14).set();
            fila++;
            for (var linea : documento.encabezado()) hoja.value(fila++, 0, linea);
            fila++; // línea en blanco

            var columnas = documento.columnas();
            for (int c = 0; c < columnas.size(); c++) {
                hoja.value(fila, c, columnas.get(c).nombre());
                hoja.style(fila, c).bold().fillColor("E5E7EB").set();
                hoja.width(c, ancho(columnas.get(c).tipo()));
            }
            fila++;

            for (var datos : documento.filas()) {
                for (int c = 0; c < columnas.size() && c < datos.size(); c++) {
                    celda(hoja, fila, c, columnas.get(c).tipo(), datos.get(c));
                }
                fila++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el xlsx del reporte", e);
        }
        return salida.toByteArray();
    }

    private static void celda(Worksheet hoja, int fila, int columna, TipoColumna tipo, Object valor) {
        if (valor == null) return;
        switch (tipo) {
            case TEXTO -> hoja.value(fila, columna, valor.toString());
            case ENTERO -> hoja.value(fila, columna, (Number) valor);
            case MONEDA -> {
                hoja.value(fila, columna, (Number) valor);
                hoja.style(fila, columna).format(FORMATO_MONEDA).set();
            }
            case FECHA_HORA -> {
                hoja.value(fila, columna, (LocalDateTime) valor);
                hoja.style(fila, columna).format(FORMATO_FECHA_HORA).set();
            }
        }
    }

    private static double ancho(TipoColumna tipo) {
        return switch (tipo) {
            case TEXTO -> 32;
            case ENTERO -> 12;
            case MONEDA -> 16;
            case FECHA_HORA -> 20;
        };
    }

    /** Excel limita el nombre a 31 caracteres y prohíbe {@code []:*?/\}. */
    private static String nombreDeHoja(String titulo) {
        var nombre = titulo.replaceAll("[\\[\\]:*?/\\\\]", " ").strip();
        if (nombre.isEmpty()) nombre = "Reporte";
        return nombre.length() > MAX_NOMBRE_HOJA ? nombre.substring(0, MAX_NOMBRE_HOJA) : nombre;
    }
}
