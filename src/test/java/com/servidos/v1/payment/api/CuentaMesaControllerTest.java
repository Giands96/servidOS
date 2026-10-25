package com.servidos.v1.payment.api;

import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.payment.api.dto.PagarCuentaMesaRequest;
import com.servidos.v1.payment.application.cuenta.ConsultarCuentaMesaUseCase;
import com.servidos.v1.payment.application.cuenta.CuentaMesa;
import com.servidos.v1.payment.application.cuenta.CuentaPagada;
import com.servidos.v1.payment.application.cuenta.PagarCuentaMesaCommand;
import com.servidos.v1.payment.application.cuenta.PagarCuentaMesaUseCase;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.shared.security.CurrentUser;
import jakarta.validation.Validation;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaMesaControllerTest {

    static final String ROLES_DE_CAJA = "hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO')";
    static final String ROLES_DE_SALON = "hasAnyRole('ADMINISTRADOR','RECEPCION','CAJERO','MESERO')";

    @Mock ConsultarCuentaMesaUseCase consultarCuentaMesaUseCase;
    @Mock PagarCuentaMesaUseCase pagarCuentaMesaUseCase;

    CuentaMesaController controller;

    @BeforeEach
    void setUp() {
        controller = new CuentaMesaController(consultarCuentaMesaUseCase, pagarCuentaMesaUseCase);
        TenantContext.setRestauranteId(100L);
        CurrentUser.setCurrentUser(9L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        CurrentUser.clear();
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
    void consultarLoHacenLosRolesDeCajaYElMesero() throws Exception {
        assertEquals(ROLES_DE_SALON, permisos("consultar", Long.class));
    }

    @Test
    void pagarTomaTenantYCajeroDelContextoYDa201() {
        var cuenta = new CuentaPagada(55L, 7L, new BigDecimal("75.50"), MetodoPago.EFECTIVO, new BigDecimal("24.50"),
                List.of(new CuentaPagada.Pago(101L, 10L, new BigDecimal("50.00")),
                        new CuentaPagada.Pago(102L, 11L, new BigDecimal("25.50"))));
        when(pagarCuentaMesaUseCase.ejecutar(
                new PagarCuentaMesaCommand(7L, MetodoPago.EFECTIVO, new BigDecimal("100.00"), "ref", List.of(10L, 11L)),
                100L, 9L)).thenReturn(cuenta);

        var resp = controller.pagar(7L, new PagarCuentaMesaRequest(
                MetodoPago.EFECTIVO, new BigDecimal("100.00"), "ref", List.of(10L, 11L)));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        var body = resp.getBody();
        assertEquals(55L, body.cuentaId());
        assertEquals(7L, body.mesaId());
        assertEquals(0, new BigDecimal("75.50").compareTo(body.total()));
        assertEquals(MetodoPago.EFECTIVO, body.metodoPago());
        assertEquals(0, new BigDecimal("24.50").compareTo(body.vuelto()));
        assertEquals(2, body.pagos().size());
        assertEquals(101L, body.pagos().get(0).pagoId());
        assertEquals(10L, body.pagos().get(0).pedidoId());
        assertEquals(0, new BigDecimal("50.00").compareTo(body.pagos().get(0).monto()));
    }

    @Test
    void pagarSoloLoHacenLosRolesDeCaja() throws Exception {
        assertEquals(ROLES_DE_CAJA, permisos("pagar", Long.class, PagarCuentaMesaRequest.class));
    }

    @Test
    void elRequestDePagoValidaMetodoPedidosYReferencia() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertTrue(validator.validate(new PagarCuentaMesaRequest(MetodoPago.YAPE, null, null, List.of(1L))).isEmpty());
        assertEquals(1, validator.validate(new PagarCuentaMesaRequest(null, null, null, List.of(1L))).size());
        assertEquals(1, validator.validate(new PagarCuentaMesaRequest(MetodoPago.YAPE, null, null, List.of())).size());
        assertEquals(1, validator.validate(new PagarCuentaMesaRequest(MetodoPago.YAPE, null, null, null)).size());
        assertEquals(1, validator.validate(new PagarCuentaMesaRequest(
                MetodoPago.YAPE, null, "x".repeat(101), List.of(1L))).size());
        assertEquals(1, validator.validate(new PagarCuentaMesaRequest(
                MetodoPago.EFECTIVO, new BigDecimal("-1"), null, List.of(1L))).size());
    }
}
