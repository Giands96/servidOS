package com.servidos.v1.payment.application.pago;

public record ReembolsarPagoCommand(Long pagoId, String motivo) {}
