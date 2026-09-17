package com.servidos.v1.payment.infrastructure;

import com.servidos.v1.ordering.application.PedidoPagoPort;
import com.servidos.v1.payment.domain.EstadoPago;
import com.servidos.v1.payment.infrastructure.jpa.PagoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PagoEstadoAdapter implements PedidoPagoPort {
    private final PagoJpaRepository pagoRepository;

    @Override
    public boolean estaPagado(Long pedidoId, Long restauranteId) {
        return pagoRepository.existsByPedidoIdAndRestauranteIdAndEstado(pedidoId, restauranteId, EstadoPago.PAGADO);
    }
}
