package com.servidos.v1.reporting.application.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatoExportTest {

    @Test
    void pdf() {
        assertEquals("application/pdf", FormatoExport.PDF.contentType());
        assertEquals("pdf", FormatoExport.PDF.extension());
    }

    @Test
    void xlsx() {
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                FormatoExport.XLSX.contentType());
        assertEquals("xlsx", FormatoExport.XLSX.extension());
    }
}
