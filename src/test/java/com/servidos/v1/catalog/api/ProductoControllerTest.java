package com.servidos.v1.catalog.api;

import com.servidos.v1.catalog.api.dto.ActualizarProductoRequest;
import com.servidos.v1.catalog.api.dto.CrearProductoRequest;
import com.servidos.v1.catalog.application.producto.ActualizarProductoUseCase;
import com.servidos.v1.catalog.application.producto.CrearProductoUseCase;
import com.servidos.v1.catalog.application.producto.ListarProductosUseCase;
import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.domain.Producto;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    @Mock CrearProductoUseCase crearProductoUseCase;
    @Mock ActualizarProductoUseCase actualizarProductoUseCase;
    @Mock ListarProductosUseCase listarProductosUseCase;

    ProductoController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductoController(crearProductoUseCase, actualizarProductoUseCase, listarProductosUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Producto producto(Long id) {
        return Producto.builder().producto_id(id).restaurante_id(100L).categoria_id(7L)
                .nombre("Ceviche").precio(new BigDecimal("25.00"))
                .estado(EstadoProducto.DISPONIBLE).build();
    }

    @Test
    void crearDelegaYDa201() {
        when(crearProductoUseCase.ejecutar(any(), eq(100L))).thenReturn(producto(1L));

        var resp = controller.crear(new CrearProductoRequest("Ceviche", "Pesca del día", 7L,
                new BigDecimal("25.00"), 15, EstadoProducto.DISPONIBLE, null));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(1L, resp.getBody().productoId());
        assertEquals("Ceviche", resp.getBody().nombre());
    }

    @Test
    void actualizarDelegaYDa200() {
        when(actualizarProductoUseCase.actualizar(any(), eq(100L))).thenReturn(producto(1L));

        var resp = controller.actualizar(1L, new ActualizarProductoRequest("Ceviche", "Pesca del día",
                7L, null, new BigDecimal("27.00"), 15, EstadoProducto.DISPONIBLE));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(actualizarProductoUseCase).actualizar(any(), eq(100L));
    }

    @Test
    void listarPasaCategoriaYDa200() {
        var pageable = PageRequest.of(0, 20);
        when(listarProductosUseCase.listar(100L, 7L, pageable))
                .thenReturn(new PageImpl<>(List.of(producto(1L))));

        var resp = controller.listar(7L, pageable);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getTotalElements());
    }

    @Test
    void obtenerDa200() {
        when(listarProductosUseCase.obtener(100L, 1L)).thenReturn(producto(1L));

        var resp = controller.obtener(1L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1L, resp.getBody().productoId());
    }
}
