package com.servidos.v1.payment.api;

import com.servidos.v1.payment.api.dto.PagoResponse;
import com.servidos.v1.payment.api.dto.RegistrarPagoRequest;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.domain.Pago;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoControllerTest {

    @Mock RegistrarPagoUseCase registrarPagoUseCase;

    PagoController controller;

    @BeforeEach
    void setUp() {
        controller = new PagoController(registrarPagoUseCase);
        TenantContext.setRestauranteId(100L);
        CurrentUser.setCurrentUser(7L);
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        CurrentUser.clear();
    }

    private static Pago pago() {
        return Pago.builder().pago_id(5L).pedido_id(1L).restaurante_id(100L).usuario_id(7L)
                .metodo_pago(MetodoPago.EFECTIVO).monto(new BigDecimal("50.00"))
                .vuelto(new BigDecimal("10.00")).estado(EstadoPago.PAGADO).build();
    }

    @Test
    void registrarDelegaYDa201() {
        when(registrarPagoUseCase.ejecutar(any(), eq(100L), eq(7L))).thenReturn(pago());

        var resp = controller.registrar(new RegistrarPagoRequest(
                1L, MetodoPago.EFECTIVO, new BigDecimal("60.00"), null));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(5L, resp.getBody().pagoId());
        assertEquals(new BigDecimal("10.00"), resp.getBody().vuelto());
        verify(registrarPagoUseCase).ejecutar(any(), eq(100L), eq(7L));
    }
}
