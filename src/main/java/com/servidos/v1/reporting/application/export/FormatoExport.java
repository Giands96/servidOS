package com.servidos.v1.reporting.application.export;

public enum FormatoExport {
    PDF("application/pdf", "pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String contentType;
    private final String extension;

    FormatoExport(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    /** Extensión del archivo, sin punto. */
    public String extension() {
        return extension;
    }
}
