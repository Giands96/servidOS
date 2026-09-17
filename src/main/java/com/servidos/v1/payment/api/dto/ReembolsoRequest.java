package com.servidos.v1.payment.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReembolsoRequest(
        @NotBlank String motivo) {}
