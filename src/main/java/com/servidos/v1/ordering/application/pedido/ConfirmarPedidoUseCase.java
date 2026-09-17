package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfirmarPedidoUseCase {
    private final CambiarEstadoPedidoUseCase cambiarEstado;

    public Pedido ejecutar(Long pedidoId, Long restauranteId) {
        if (pedidoId == null) {
            throw new BusinessException("Pedido no identificado");
        }
        if (restauranteId == null) {
            throw new BusinessException("Restaurante no identificado");
        }
        return cambiarEstado.ejecutar(pedidoId, restauranteId, EstadoPedido.EN_PREPARACION);
    }
}
