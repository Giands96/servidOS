package com.servidos.v1.tenant.domain.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RestauranteCreadoEvent {
    private Long restauranteId;
    private String slug;
}
