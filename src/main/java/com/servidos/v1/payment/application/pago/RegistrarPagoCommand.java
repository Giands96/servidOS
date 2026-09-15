package com.servidos.v1.payment.application.pago;

import com.servidos.v1.payment.domain.MetodoPago;
import java.math.BigDecimal;

public record RegistrarPagoCommand(Long pedidoId, MetodoPago metodoPago, BigDecimal montoEntregado, String referenciaExterna) {}
