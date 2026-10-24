package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.payment.domain.MetodoPago;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code pedidoIds} son los pedidos que el cajero vio en la cuenta: el cobro solo procede si
 * coinciden exactamente con lo pendiente al momento de cobrar.
 */
public record PagarCuentaMesaCommand(Long mesaId, MetodoPago metodoPago, BigDecimal montoEntregado,
                                     String referenciaExterna, List<Long> pedidoIds) {}
