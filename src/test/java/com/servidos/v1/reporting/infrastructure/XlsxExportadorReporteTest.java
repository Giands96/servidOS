package com.servidos.v1.reporting.infrastructure;

import com.servidos.v1.reporting.application.export.DocumentoReporte;
import com.servidos.v1.reporting.application.export.FormatoExport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxExportadorReporteTest {

    final XlsxExportadorReporte exportador = new XlsxExportadorReporte();

    /**
     * El xlsx es un zip; lo abrimos a mano para no sumar una dependencia de lectura. Se usa ZipFile
     * (directorio central) porque fastexcel escribe las entradas en streaming y ZipInputStream no las lee.
     */
    private static Map<String, String> partes(byte[] xlsx) throws IOException {
        Map<String, String> partes = new HashMap<>();
        var archivo = Files.createTempFile("reporte-", ".xlsx");
        try {
            Files.write(archivo, xlsx);
            try (var zip = new ZipFile(archivo.toFile())) {
                for (var entradas = zip.entries(); entradas.hasMoreElements(); ) {
                    var e = entradas.nextElement();
                    try (var in = zip.getInputStream(e)) {
                        partes.put(e.getName(), sinReferencias(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
        } finally {
            Files.deleteIfExists(archivo);
        }
        return partes;
    }

    /** fastexcel escribe los no ASCII como referencias numéricas (&#xb7;); las volvemos texto para comparar. */
    private static String sinReferencias(String xml) {
        return Pattern.compile("&#x([0-9a-fA-F]+);").matcher(xml)
                .replaceAll(m -> Matcher.quoteReplacement(Character.toString(Integer.parseInt(m.group(1), 16))));
    }

    @Test
    void esElExportadorDeXlsx() {
        assertEquals(FormatoExport.XLSX, exportador.formato());
    }

    @Test
    void generaUnaHojaConTituloEncabezadoYColumnas() throws IOException {
        var partes = partes(exportador.exportar(DocumentoDePrueba.conDosFilas()));

        assertTrue(partes.containsKey("xl/worksheets/sheet1.xml"), partes.keySet().toString());
        var textos = partes.getOrDefault("xl/sharedStrings.xml", "") + partes.get("xl/worksheets/sheet1.xml");
        assertTrue(textos.contains("Reporte de ventas"), textos);
        for (var linea : DocumentoDePrueba.ENCABEZADO) assertTrue(textos.contains(linea), linea + " en " + textos);
        for (var columna : DocumentoDePrueba.COLUMNAS) assertTrue(textos.contains(columna.nombre()), columna.nombre());
        assertTrue(textos.contains("Ceviche mixto"));
        assertTrue(textos.contains("Ñoquis"));
    }

    @Test
    void losNumerosVanComoNumerosNoComoTexto() throws IOException {
        var hoja = partes(exportador.exportar(DocumentoDePrueba.conDosFilas())).get("xl/worksheets/sheet1.xml");

        assertTrue(hoja.contains("<v>1234.5"), hoja);
        assertTrue(hoja.contains("<v>15</v>"), hoja);
        assertFalse(hoja.contains("S/ 1,234.50"), "el monto no debe ir como texto formateado");
    }

    @Test
    void aplicaFormatoDeMonedaYDeFechaHora() throws IOException {
        var estilos = partes(exportador.exportar(DocumentoDePrueba.conDosFilas())).get("xl/styles.xml");

        assertTrue(estilos.contains("#,##0.00"), estilos);
        assertTrue(estilos.contains("dd/MM/yyyy HH:mm"), estilos);
    }

    @Test
    void conMuchasFilasLasEscribeTodas() throws IOException {
        var hoja = partes(exportador.exportar(DocumentoDePrueba.conFilas(500))).get("xl/worksheets/sheet1.xml");

        // título + 4 líneas de encabezado + línea en blanco + cabecera + 500 filas
        assertTrue(hoja.contains("<row r=\"507\""), "falta la última fila");
    }

    @Test
    void sinFilasGeneraIgualLaHoja() throws IOException {
        var vacio = new DocumentoReporte("Reporte de ventas", DocumentoDePrueba.ENCABEZADO,
                DocumentoDePrueba.COLUMNAS, List.of());

        var partes = partes(exportador.exportar(vacio));

        assertTrue(partes.containsKey("xl/worksheets/sheet1.xml"));
    }
}
