package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.domain.Mesa;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.MesaMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baja lógica de una mesa: solo si está LIBRE y sin pedidos abiertos (ver
 * {@link PedidoJpaRepository#contarAbiertosEnMesa}), para no dejar pedidos colgados de una mesa
 * dada de baja. La fila no se borra: los pedidos históricos y los reportes siguen apuntando a ella.
 */
@Service
@RequiredArgsConstructor
public class DesactivarMesaUseCase {

    private final MesaJpaRepository mesaRepository;
    private final PedidoJpaRepository pedidoRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public Mesa ejecutar(Long mesaId, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (mesaId == null) throw new BusinessException("Mesa no identificada");

        // Lock: un pedido de mesa nuevo no se cuela entre la revisión y la baja.
        var entity = mesaRepository.bloquearPorIdYRestaurante(mesaId, restauranteId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada"));
        Mesa mesa = mesaMapper.toDomain(entity);
        mesa.desactivar();
        if (pedidoRepository.contarAbiertosEnMesa(mesaId, restauranteId) > 0) {
            throw new BusinessException("La mesa tiene pedidos abiertos");
        }
        mesaMapper.updateEntity(mesa, entity);
        return mesaMapper.toDomain(mesaRepository.saveAndFlush(entity));
    }
}
