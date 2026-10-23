package com.servidos.v1.reporting.application;

import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaPedido;
import com.servidos.v1.reporting.domain.VentaProducto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Lectura de ventas para reportes. Una venta es un pedido con pago PAGADO y no CANCELADO;
 * el rango se mide sobre la fecha de pago.
 */
public interface ReporteVentasQuery {

    Page<VentaPedido> ventas(Long restauranteId, RangoFechas rango, OrdenVentas orden, Pageable pageable);

    /** Todas las ventas del rango, sin paginar (para exportar). */
    List<VentaPedido> todasLasVentas(Long restauranteId, RangoFechas rango, OrdenVentas orden);

    ResumenVentas resumen(Long restauranteId, RangoFechas rango);

    /** Ranking por monto vendido, de mayor a menor. */
    List<VentaProducto> productosVendidos(Long restauranteId, RangoFechas rango);

    String nombreRestaurante(Long restauranteId);
}
