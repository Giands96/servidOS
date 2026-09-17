package com.servidos.v1.payment.api;

import com.servidos.v1.payment.api.dto.PagoResponse;
import com.servidos.v1.payment.api.dto.ReembolsoRequest;
import com.servidos.v1.payment.application.pago.RegistrarPagoUseCase;
import com.servidos.v1.payment.application.pago.RegistrarReembolsoUseCase;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.domain.MetodoPago;
import com.servidos.v1.payment.domain.Pago;
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
    @Mock RegistrarReembolsoUseCase registrarReembolsoUseCase;

    PagoController controller;

    @BeforeEach
    void setUp() {
        controller = new PagoController(registrarPagoUseCase, registrarReembolsoUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Pago pago(EstadoPago estado) {
        return Pago.builder().pago_id(5L).pedido_id(1L).restaurante_id(100L).usuario_id(2L)
                .metodo_pago(MetodoPago.EFECTIVO).monto(new BigDecimal("50.00"))
                .estado(estado).build();
    }

    @Test
    void reembolsarDelegaYDa200() {
        when(registrarReembolsoUseCase.ejecutar(any(), eq(100L)))
                .thenReturn(pago(EstadoPago.REEMBOLSADO));

        var resp = controller.reembolsar(5L, new ReembolsoRequest("Cobro duplicado"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(5L, resp.getBody().pagoId());
        assertEquals(EstadoPago.REEMBOLSADO, resp.getBody().estado());
        verify(registrarReembolsoUseCase).ejecutar(any(), eq(100L));
    }
}
