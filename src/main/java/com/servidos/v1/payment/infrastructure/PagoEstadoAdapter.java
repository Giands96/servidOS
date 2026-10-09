package com.servidos.v1.payment.infrastructure;

import com.servidos.v1.ordering.application.PedidoPagoPort;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PagoEstadoAdapter implements PedidoPagoPort {
    private final PagoJpaRepository pagoRepository;

    @Override
    public boolean estaPagado(Long pedidoId, Long restauranteId) {
        return pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(pedidoId, restauranteId, EstadoPago.PAGADO);
    }

    @Override
    public Map<Long, String> estadosDePago(Collection<Long> pedidoIds, Long restauranteId) {
        if (pedidoIds.isEmpty()) return Map.of();
        return pagoRepository.findByRestauranteIdAndPedidoIdIn(restauranteId, pedidoIds).stream()
                .collect(Collectors.toMap(PagoJpaRepository.EstadoPagoDePedido::getPedidoId, p -> p.getEstado().name()));
    }
}
