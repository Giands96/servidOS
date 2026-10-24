package com.servidos.v1.payment.api;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.payment.application.cuenta.ConsultarCuentaMesaUseCase;
import com.servidos.v1.payment.application.cuenta.CuentaMesa;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaMesaControllerTest {

    static final String ROLES_DE_CAJA = "hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO')";

    @Mock ConsultarCuentaMesaUseCase consultarCuentaMesaUseCase;

    CuentaMesaController controller;

    @BeforeEach
    void setUp() {
        controller = new CuentaMesaController(consultarCuentaMesaUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static String permisos(String metodo, Class<?>... tipos) throws NoSuchMethodException {
        Method m = CuentaMesaController.class.getMethod(metodo, tipos);
        return m.getAnnotation(PreAuthorize.class).value();
    }

    @Test
    void consultarTomaElTenantDelContextoYDa200() {
        var item = new CuentaMesa.Item("Lomo saltado", 2, new BigDecimal("50.00"));
        var pedido = new CuentaMesa.Pedido(10L, TipoPedido.MESA, EstadoPedido.ENTREGADO,
                LocalDateTime.of(2026, 10, 24, 12, 0), List.of(item), "sin ají", new BigDecimal("50.00"));
        when(consultarCuentaMesaUseCase.ejecutar(7L, 100L)).thenReturn(new CuentaMesa(
                7L, 5, EstadoMesa.OCUPADA, 3, 4, List.of(pedido), new BigDecimal("50.00")));

        var resp = controller.consultar(7L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        var body = resp.getBody();
        assertEquals(7L, body.mesaId());
        assertEquals(5, body.numero());
        assertEquals(EstadoMesa.OCUPADA, body.estado());
        assertEquals(3, body.comensales());
        assertEquals(4, body.capacidad());
        assertEquals(0, new BigDecimal("50.00").compareTo(body.total()));
        assertEquals(1, body.pedidos().size());
        var p = body.pedidos().get(0);
        assertEquals(10L, p.pedidoId());
        assertEquals(TipoPedido.MESA, p.tipoPedido());
        assertEquals("sin ají", p.observacion());
        assertEquals("Lomo saltado", p.items().get(0).nombre());
        assertEquals(2, p.items().get(0).cantidad());
        verify(consultarCuentaMesaUseCase).ejecutar(7L, 100L);
    }

    @Test
    void consultarSoloLoHacenLosRolesDeCaja() throws Exception {
        assertEquals(ROLES_DE_CAJA, permisos("consultar", Long.class));
    }
}
