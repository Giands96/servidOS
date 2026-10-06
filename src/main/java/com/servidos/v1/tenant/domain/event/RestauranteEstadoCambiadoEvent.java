package com.servidos.v1.tenant.domain.event;

import com.servidos.v1.tenant.domain.EstadoRestaurante;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Se publica cada vez que la plataforma activa o suspende un restaurante. Queda como
 * traza de auditoría de una decisión que le corta el servicio a un cliente: quién lo
 * suspendió (via CurrentUser en el log), cuándo y hacia qué estado.
 */
@Getter
@AllArgsConstructor
public class RestauranteEstadoCambiadoEvent {
    private Long restauranteId;
    private EstadoRestaurante estadoAnterior;
    private EstadoRestaurante estadoNuevo;
}