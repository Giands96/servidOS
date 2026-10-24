package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La mesa de un pedido nuevo: tiene que ser del restaurante y estar activa y, si el pedido es de mesa, queda
 * OCUPADA. Se ejecuta dentro de la transacción de {@code CrearPedidoUseCase}: el lock de la
 * mesa dura hasta que el pedido está guardado, y una liberación automática concurrente espera
 * a verlo (ver {@code LiberarMesaAlCerrarPedidoUseCase}).
 */
@Service
@RequiredArgsConstructor
public class AsignarMesaAPedidoUseCase {

    private final MesaJpaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public void ejecutar(Long mesaId, TipoPedido tipoPedido, Long restauranteId) {
        if (mesaId == null) return;
        if (tipoPedido != TipoPedido.MESA) {
            // Un delivery/recojo con mesaId no ocupa nada; solo se evita apuntar a una mesa ajena o dada de baja.
            var entity = mesaRepository.findByMesaIdAndRestauranteId(mesaId, restauranteId)
                    .orElseThrow(() -> new BusinessException("La mesa no existe"));
            if (!entity.isActiva()) throw new BusinessException("La mesa está inactiva");
            return;
        }
        var entity = mesaRepository.bloquearPorIdYRestaurante(mesaId, restauranteId)
                .orElseThrow(() -> new BusinessException("La mesa no existe"));
        Mesa mesa = mesaMapper.toDomain(entity);
        mesa.ocuparPorPedido();
        mesaMapper.updateEntity(mesa, entity);
        mesaRepository.saveAndFlush(entity);
    }
}
