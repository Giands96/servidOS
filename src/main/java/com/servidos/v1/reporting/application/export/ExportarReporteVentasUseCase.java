package com.servidos.v1.reporting.application.export;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.Periodo;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Exporta todas las ventas del período (sin paginar), en el orden pedido, a PDF o xlsx. */
@Service
@RequiredArgsConstructor
public class ExportarReporteVentasUseCase {

    private static final List<Columna> COLUMNAS = List.of(
            new Columna("N° pedido", TipoColumna.ENTERO),
            new Columna("Fecha de pago", TipoColumna.FECHA_HORA),
            new Columna("Tipo", TipoColumna.TEXTO),
            new Columna("Método de pago", TipoColumna.TEXTO),
            new Columna("Total", TipoColumna.MONEDA));

    private final ReporteVentasQuery query;
    private final List<ExportadorReporte> exportadores;

    /** @param orden null equivale a {@link OrdenVentas#POR_DEFECTO} */
    @Transactional(readOnly = true)
    public ArchivoReporte exportar(Long restauranteId, Periodo periodo, LocalDate fecha, OrdenVentas orden,
                                   FormatoExport formato) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (periodo == null) throw new BusinessException("El período es obligatorio");
        if (formato == null) throw new BusinessException("El formato es obligatorio");
        var rango = periodo.rango(fecha);
        var exportador = ArmadoDeReporte.exportador(exportadores, formato);
        var criterio = orden == null ? OrdenVentas.POR_DEFECTO : orden;

        var resumen = query.resumen(restauranteId, rango);
        var ventas = query.todasLasVentas(restauranteId, rango, criterio);
        List<List<Object>> filas = ventas.stream()
                .map(v -> List.<Object>of(v.pedidoId(), v.fechaPago(), v.tipoPedido(), v.metodoPago(), v.total()))
                .toList();
        var documento = new DocumentoReporte("Reporte de ventas",
                ArmadoDeReporte.encabezado(query.nombreRestaurante(restauranteId), periodo, rango, resumen),
                COLUMNAS, filas);

        return new ArchivoReporte(ArmadoDeReporte.nombreDeArchivo("ventas", periodo, rango, formato),
                formato.contentType(), exportador.exportar(documento));
    }
}
