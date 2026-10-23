package com.servidos.v1.reporting.infrastructure;

import com.servidos.v1.reporting.application.export.Columna;
import com.servidos.v1.reporting.application.export.DocumentoReporte;
import com.servidos.v1.reporting.application.export.ExportadorReporte;
import com.servidos.v1.reporting.application.export.FormatoExport;
import com.servidos.v1.reporting.application.export.FormatosReporte;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** PDF A4 con título, líneas de encabezado y la tabla (la cabecera se repite en cada página). */
@Component
public class PdfExportadorReporte implements ExportadorReporte {

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font ENCABEZADO = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Color FONDO_CABECERA = new Color(0xE5, 0xE7, 0xEB);

    @Override
    public FormatoExport formato() {
        return FormatoExport.PDF;
    }

    @Override
    public byte[] exportar(DocumentoReporte documento) {
        var salida = new ByteArrayOutputStream();
        var pdf = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(pdf, salida);
            pdf.addTitle(documento.titulo());
            pdf.open();

            pdf.add(new Paragraph(documento.titulo(), TITULO));
            for (var linea : documento.encabezado()) pdf.add(new Paragraph(linea, ENCABEZADO));
            if (!documento.columnas().isEmpty()) pdf.add(tabla(documento));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        } finally {
            pdf.close();
        }
        return salida.toByteArray();
    }

    private PdfPTable tabla(DocumentoReporte documento) throws DocumentException {
        var columnas = documento.columnas();
        var tabla = new PdfPTable(columnas.size());
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(12);
        tabla.setWidths(anchos(columnas));
        tabla.setHeaderRows(1);

        for (var columna : columnas) {
            var celda = celda(columna.nombre(), CABECERA, alineacion(columna));
            celda.setBackgroundColor(FONDO_CABECERA);
            tabla.addCell(celda);
        }
        for (var fila : documento.filas()) {
            for (int i = 0; i < columnas.size(); i++) {
                var columna = columnas.get(i);
                tabla.addCell(celda(texto(columna, i < fila.size() ? fila.get(i) : null), CELDA, alineacion(columna)));
            }
        }
        return tabla;
    }

    private static PdfPCell celda(String texto, Font fuente, int alineacion) {
        var celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(4);
        return celda;
    }

    private static String texto(Columna columna, Object valor) {
        if (valor == null) return "";
        return switch (columna.tipo()) {
            case MONEDA -> FormatosReporte.moneda((BigDecimal) valor);
            case FECHA_HORA -> FormatosReporte.fechaHora((LocalDateTime) valor);
            case ENTERO, TEXTO -> valor.toString();
        };
    }

    private static int alineacion(Columna columna) {
        return switch (columna.tipo()) {
            case ENTERO, MONEDA -> Element.ALIGN_RIGHT;
            case TEXTO, FECHA_HORA -> Element.ALIGN_LEFT;
        };
    }

    private static float[] anchos(java.util.List<Columna> columnas) {
        var anchos = new float[columnas.size()];
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = switch (columnas.get(i).tipo()) {
                case TEXTO -> 3f;
                case ENTERO -> 1.3f;
                case MONEDA -> 1.8f;
                case FECHA_HORA -> 2.2f;
            };
        }
        return anchos;
    }
}
