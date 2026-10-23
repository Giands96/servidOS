package com.servidos.v1.reporting.application.export;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.reporting.domain.VentaProducto;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Exporta el ranking de productos vendidos del período a PDF o xlsx. */
@Service
@RequiredArgsConstructor
public class ExportarReporteProductosUseCase {

    private static final List<Columna> COLUMNAS = List.of(
            new Columna("#", TipoColumna.ENTERO),
            new Columna("Producto", TipoColumna.TEXTO),
            new Columna("Cantidad", TipoColumna.ENTERO),
            new Columna("Monto vendido", TipoColumna.MONEDA));

    private final ReporteVentasQuery query;
    private final List<ExportadorReporte> exportadores;

    @Transactional(readOnly = true)
    public ArchivoReporte exportar(Long restauranteId, Periodo periodo, LocalDate fecha, FormatoExport formato) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (periodo == null) throw new BusinessException("El período es obligatorio");
        if (formato == null) throw new BusinessException("El formato es obligatorio");
        var rango = periodo.rango(fecha);
        var exportador = ArmadoDeReporte.exportador(exportadores, formato);

        var resumen = query.resumen(restauranteId, rango);
        var productos = query.productosVendidos(restauranteId, rango);
        List<List<Object>> filas = new ArrayList<>(productos.size());
        for (int i = 0; i < productos.size(); i++) {
            VentaProducto p = productos.get(i);
            filas.add(List.of((long) i + 1, p.nombre(), p.cantidad(), p.monto()));
        }
        var documento = new DocumentoReporte("Reporte de productos",
                ArmadoDeReporte.encabezado(query.nombreRestaurante(restauranteId), periodo, rango, resumen),
                COLUMNAS, filas);

        return new ArchivoReporte(ArmadoDeReporte.nombreDeArchivo("productos", periodo, rango, formato),
                formato.contentType(), exportador.exportar(documento));
    }
}
