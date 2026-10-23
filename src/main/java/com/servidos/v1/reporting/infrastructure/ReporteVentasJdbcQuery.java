package com.servidos.v1.reporting.infrastructure;

import com.servidos.v1.reporting.application.ReporteVentasQuery;
import com.servidos.v1.reporting.domain.OrdenVentas;
import com.servidos.v1.reporting.domain.RangoFechas;
import com.servidos.v1.reporting.domain.ResumenVentas;
import com.servidos.v1.reporting.domain.VentaPedido;
import com.servidos.v1.reporting.domain.VentaProducto;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SQL nativo de los reportes. Venta = pedido con pago PAGADO y no CANCELADO, medida por
 * {@code pago.fecha_pago}. Todo se filtra por restaurante en pedido, pago y detalle_pedido.
 */
@Repository
@RequiredArgsConstructor
public class ReporteVentasJdbcQuery implements ReporteVentasQuery {

    /** Ventas del restaurante en [desde, hasta). */
    private static final String VENTAS_DEL_PERIODO = """
            FROM pago pg
            JOIN pedido p ON p.pedido_id = pg.pedido_id AND p.restaurante_id = pg.restaurante_id
            WHERE pg.restaurante_id = :restauranteId
              AND pg.estado = 'PAGADO'
              AND p.estado <> 'CANCELADO'
              AND pg.fecha_pago >= :desde
              AND pg.fecha_pago < :hasta
            """;

    private static final String SELECT_VENTAS = """
            SELECT p.pedido_id, pg.fecha_pago, p.tipo_pedido, pg.metodo_pago, p.total
            """ + VENTAS_DEL_PERIODO;

    private static final String SELECT_RESUMEN = """
            SELECT COUNT(*) AS cantidad, COALESCE(SUM(p.total), 0) AS total
            """ + VENTAS_DEL_PERIODO;

    /**
     * Nombre: último snapshot no nulo del detalle (por fecha de pago); si no hay, el del producto.
     * Orden: monto, cantidad, nombre; producto_id al final para que sea determinista.
     */
    private static final String SELECT_PRODUCTOS = """
            SELECT d.producto_id,
                   COALESCE(
                       (array_agg(d.nombre_producto ORDER BY pg.fecha_pago DESC, d.detalle_id DESC)
                            FILTER (WHERE d.nombre_producto IS NOT NULL))[1],
                       MAX(pr.nombre),
                       'Producto ' || d.producto_id) AS nombre,
                   SUM(d.cantidad) AS cantidad,
                   SUM(d.subtotal) AS monto
            FROM pago pg
            JOIN pedido p ON p.pedido_id = pg.pedido_id AND p.restaurante_id = pg.restaurante_id
            JOIN detalle_pedido d ON d.pedido_id = p.pedido_id AND d.restaurante_id = p.restaurante_id
            LEFT JOIN producto pr ON pr.producto_id = d.producto_id AND pr.restaurante_id = d.restaurante_id
            WHERE pg.restaurante_id = :restauranteId
              AND pg.estado = 'PAGADO'
              AND p.estado <> 'CANCELADO'
              AND pg.fecha_pago >= :desde
              AND pg.fecha_pago < :hasta
            GROUP BY d.producto_id
            ORDER BY monto DESC, cantidad DESC, nombre ASC, d.producto_id ASC
            """;

    private static final RowMapper<VentaPedido> VENTA_MAPPER = (rs, i) -> new VentaPedido(
            rs.getLong("pedido_id"),
            rs.getObject("fecha_pago", LocalDateTime.class),
            rs.getString("tipo_pedido"),
            rs.getString("metodo_pago"),
            rs.getBigDecimal("total"));

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Page<VentaPedido> ventas(Long restauranteId, RangoFechas rango, OrdenVentas orden, Pageable pageable) {
        var params = parametros(restauranteId, rango)
                .addValue("limite", pageable.getPageSize())
                .addValue("desplazamiento", pageable.getOffset());
        var contenido = jdbc.query(
                SELECT_VENTAS + " ORDER BY " + ordenSql(orden) + " LIMIT :limite OFFSET :desplazamiento",
                params, VENTA_MAPPER);
        var total = jdbc.queryForObject("SELECT COUNT(*) " + VENTAS_DEL_PERIODO, params, Long.class);
        return new PageImpl<>(contenido, pageable, total == null ? 0 : total);
    }

    @Override
    public List<VentaPedido> todasLasVentas(Long restauranteId, RangoFechas rango, OrdenVentas orden) {
        return jdbc.query(SELECT_VENTAS + " ORDER BY " + ordenSql(orden),
                parametros(restauranteId, rango), VENTA_MAPPER);
    }

    @Override
    public ResumenVentas resumen(Long restauranteId, RangoFechas rango) {
        return jdbc.queryForObject(SELECT_RESUMEN, parametros(restauranteId, rango),
                (rs, i) -> new ResumenVentas(rs.getLong("cantidad"), rs.getBigDecimal("total")));
    }

    @Override
    public List<VentaProducto> productosVendidos(Long restauranteId, RangoFechas rango) {
        return jdbc.query(SELECT_PRODUCTOS, parametros(restauranteId, rango),
                (rs, i) -> new VentaProducto(
                        rs.getLong("producto_id"),
                        rs.getString("nombre"),
                        rs.getLong("cantidad"),
                        rs.getBigDecimal("monto")));
    }

    @Override
    public String nombreRestaurante(Long restauranteId) {
        var nombres = jdbc.queryForList("SELECT nombre FROM restaurante WHERE restaurante_id = :id",
                new MapSqlParameterSource("id", restauranteId), String.class);
        if (nombres.isEmpty()) throw new BusinessException("Restaurante no encontrado");
        return nombres.get(0);
    }

    private static MapSqlParameterSource parametros(Long restauranteId, RangoFechas rango) {
        return new MapSqlParameterSource()
                .addValue("restauranteId", restauranteId)
                .addValue("desde", rango.inicio())
                .addValue("hasta", rango.finExclusivo());
    }

    /** Fragmentos fijos: el orden nunca se arma con texto del usuario. */
    private static String ordenSql(OrdenVentas orden) {
        return switch (orden) {
            case MAYOR_VENTA -> "p.total DESC, pg.fecha_pago DESC, p.pedido_id DESC";
            case MAS_RECIENTES -> "pg.fecha_pago DESC, p.pedido_id DESC";
            case MAS_ANTIGUOS -> "pg.fecha_pago ASC, p.pedido_id ASC";
        };
    }
}
