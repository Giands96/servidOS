package com.servidos.v1.tenant.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PlanCambiadoEvent {
    private Long restauranteId;
    private Long viejoPlanId;
    private Long nuevoPlanId;
}
