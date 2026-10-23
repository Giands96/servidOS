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
 * Liberación manual: solo si la mesa no tiene pedidos abiertos (ver
 * {@link PedidoJpaRepository#contarAbiertosEnMesa}). Con pedidos abiertos la mesa se libera
 * sola cuando el último se entrega y se cobra, o se cancela.
 */
@Service
@RequiredArgsConstructor
public class LiberarMesaUseCase {

    private final MesaJpaRepository mesaRepository;
    private final PedidoJpaRepository pedidoRepository;
    private final MesaMapper mesaMapper;

    @Transactional
    public Mesa ejecutar(Long mesaId, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (mesaId == null) throw new BusinessException("Mesa no identificada");

        var entity = mesaRepository.bloquearPorIdYRestaurante(mesaId, restauranteId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada"));
        Mesa mesa = mesaMapper.toDomain(entity);
        // El dominio opera sobre una copia: rechaza primero la mesa que ya está LIBRE.
        mesa.liberar();
        if (pedidoRepository.contarAbiertosEnMesa(mesaId, restauranteId) > 0) {
            throw new BusinessException("La mesa tiene pedidos abiertos");
        }
        mesaMapper.updateEntity(mesa, entity);
        return mesaMapper.toDomain(mesaRepository.saveAndFlush(entity));
    }
}
