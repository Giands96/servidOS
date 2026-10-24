package com.servidos.v1.payment.application.cuenta;

import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * La cuenta de una mesa: todo lo que está pendiente de cobro en ella (ver
 * {@link PedidoJpaRepository#buscarPendientesDeCobroEnMesa}). Solo lee: no toma el lock de la mesa,
 * porque el cobro ({@code PagarCuentaMesaUseCase}) vuelve a calcular el conjunto bajo lock.
 */
@Service
@RequiredArgsConstructor
public class ConsultarCuentaMesaUseCase {

    private final MesaJpaRepository mesaRepository;
    private final PedidoJpaRepository pedidoRepository;
    private final DetallePedidoJpaRepository detalleRepository;

    @Transactional(readOnly = true)
    public CuentaMesa ejecutar(Long mesaId, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (mesaId == null) throw new BusinessException("Mesa no identificada");

        var mesa = mesaRepository.findByMesaIdAndRestauranteId(mesaId, restauranteId)
                .orElseThrow(() -> new BusinessException("Mesa no encontrada"));

        List<PedidoJpaEntity> pendientes = pedidoRepository.buscarPendientesDeCobroEnMesa(mesaId, restauranteId);
        // Una sola consulta para los ítems de todos los pedidos: sin N+1.
        Map<Long, List<CuentaMesa.Item>> itemsPorPedido = pendientes.isEmpty() ? Map.of()
                : detalleRepository.findByRestauranteIdAndPedidoIdInOrderByDetalleIdAsc(
                                restauranteId, pendientes.stream().map(PedidoJpaEntity::getPedidoId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(DetallePedidoJpaEntity::getPedidoId,
                                Collectors.mapping(d -> new CuentaMesa.Item(
                                        d.getNombreProducto(), d.getCantidad(), d.getSubtotal()),
                                        Collectors.toList())));

        List<CuentaMesa.Pedido> pedidos = pendientes.stream()
                .map(p -> new CuentaMesa.Pedido(p.getPedidoId(), p.getTipoPedido(), p.getEstado(),
                        p.getCreatedAt(), itemsPorPedido.getOrDefault(p.getPedidoId(), List.of()),
                        p.getObservacion(), p.getTotal()))
                .toList();
        BigDecimal total = pendientes.stream().map(PedidoJpaEntity::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CuentaMesa(mesa.getMesaId(), mesa.getNumero(), mesa.getEstado(), mesa.getComensales(),
                mesa.getCapacidad(), pedidos, total);
    }
}
