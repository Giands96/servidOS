package com.servidos.v1.reporting.infrastructure;

import com.servidos.v1.reporting.application.export.DocumentoReporte;
import com.servidos.v1.reporting.application.export.FormatoExport;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExportadorReporteTest {

    final PdfExportadorReporte exportador = new PdfExportadorReporte();

    private static String texto(byte[] pdf) throws IOException {
        var reader = new PdfReader(pdf);
        var extractor = new PdfTextExtractor(reader);
        var sb = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) sb.append(extractor.getTextFromPage(p)).append('\n');
        reader.close();
        return sb.toString();
    }

    @Test
    void esElExportadorDePdf() {
        assertEquals(FormatoExport.PDF, exportador.formato());
    }

    @Test
    void generaUnPdfValido() {
        var bytes = exportador.exportar(DocumentoDePrueba.conDosFilas());

        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void incluyeTituloEncabezadoColumnasYFilas() throws IOException {
        var texto = texto(exportador.exportar(DocumentoDePrueba.conDosFilas()));

        assertTrue(texto.contains("Reporte de ventas"), texto);
        for (var linea : DocumentoDePrueba.ENCABEZADO) assertTrue(texto.contains(linea), linea + " en " + texto);
        for (var columna : DocumentoDePrueba.COLUMNAS) {
            assertTrue(texto.contains(columna.nombre()), columna.nombre() + " en " + texto);
        }
        assertTrue(texto.contains("Ceviche mixto"), texto);
        assertTrue(texto.contains("Ñoquis"), texto);
    }

    @Test
    void formateaMonedaYFechas() throws IOException {
        var texto = texto(exportador.exportar(DocumentoDePrueba.conDosFilas()));

        assertTrue(texto.contains("S/ 1,234.50"), texto);
        assertTrue(texto.contains("S/ 50.00"), texto);
        assertTrue(texto.contains("23/10/2026 13:05"), texto);
        assertTrue(texto.contains("22/10/2026 20:45"), texto);
    }

    @Test
    void conMuchasFilasPaginaYRepiteLaCabeceraDeLaTabla() throws IOException {
        var bytes = exportador.exportar(DocumentoDePrueba.conFilas(150));
        var reader = new PdfReader(bytes);
        try {
            assertTrue(reader.getNumberOfPages() > 1);
            var ultima = new PdfTextExtractor(reader).getTextFromPage(reader.getNumberOfPages());
            assertTrue(ultima.contains("Producto 150"), ultima);
            assertTrue(ultima.contains("Fecha de pago"), ultima);
        } finally {
            reader.close();
        }
    }

    /** Una tabla con solo cabecera no se dibuja en OpenPDF: el período vacío tiene que verse igual. */
    @Test
    void sinFilasMuestraLasColumnasYAvisaQueNoHayVentas() throws IOException {
        var vacio = new DocumentoReporte("Reporte de ventas", DocumentoDePrueba.ENCABEZADO,
                DocumentoDePrueba.COLUMNAS, List.of());

        var texto = texto(exportador.exportar(vacio));

        assertTrue(texto.contains("Reporte de ventas"), texto);
        assertTrue(texto.contains("Chifa Central"), texto);
        for (var columna : DocumentoDePrueba.COLUMNAS) {
            assertTrue(texto.contains(columna.nombre()), columna.nombre() + " en " + texto);
        }
        assertTrue(texto.contains("Sin ventas en el período"), texto);
    }
}
