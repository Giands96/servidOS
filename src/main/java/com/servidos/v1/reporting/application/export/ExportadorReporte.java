package com.servidos.v1.reporting.application.export;

/** Dibuja un {@link DocumentoReporte} en un formato de archivo. */
public interface ExportadorReporte {

    FormatoExport formato();

    byte[] exportar(DocumentoReporte documento);
}
