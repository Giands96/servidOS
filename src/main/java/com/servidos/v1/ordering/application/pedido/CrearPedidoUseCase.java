package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.ProductCatalogPort;
import com.servidos.v1.ordering.domain.DetallePedido;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.domain.event.PedidoCreadoEvent;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.DetallePedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.DetallePedidoMapper;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CrearPedidoUseCase {
    private final PedidoJpaRepository pedidoRepository;
    private final DetallePedidoJpaRepository detalleRepository;
    private final ProductCatalogPort productoCatalog;
    private final PedidoMapper pedidoMapper;
    private final DetallePedidoMapper detalleMapper;
    private final EventPublisher eventPublisher;

    @Transactional
    public Pedido ejecutar(CrearPedidoCommand cmd, Long restauranteId) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        validar(cmd, restauranteId);

        Pedido pedidoDomain = Pedido.crear(restauranteId,
                null,
                cmd.mesaId(),
                cmd.tipoPedido(),
                cmd.observacion(),
                cmd.repartidorNombre(),
                EstadoPedido.PENDIENTE,
                BigDecimal.ZERO);
        PedidoJpaEntity pedidoEntity = pedidoMapper.toEntity(pedidoDomain);
        PedidoJpaEntity savedPedido = pedidoRepository.save(pedidoEntity);

        BigDecimal total = BigDecimal.ZERO;
        List<DetallePedidoJpaEntity> detallesToSave = new ArrayList<>();
        for (CrearPedidoItem item : cmd.items()) {
            var producto = productoCatalog.findPrecioByIdAndRestaurante(item.productoId(), restauranteId)
                    .orElseThrow(() -> new BusinessException("Producto no encontrado: " + item.productoId()));
            DetallePedido detalle = DetallePedido.crear(
                    restauranteId,
                    savedPedido.getPedidoId(),
                    item.productoId(),
                    item.cantidad(),
                    producto,
                    item.observacion());
            total = total.add(detalle.getSubtotal());
            detallesToSave.add(detalleMapper.toEntity(detalle));
        }
        detalleRepository.saveAll(detallesToSave);

        // Actualizar total del pedido
        savedPedido.setTotal(total);

        eventPublisher.publish(new PedidoCreadoEvent(savedPedido.getPedidoId(), restauranteId));

        return pedidoMapper.toDomain(savedPedido);
    }

    private void validar(CrearPedidoCommand cmd, Long restauranteId) {
        if (cmd.tipoPedido() == null) throw new BusinessException("El tipo de pedido es obligatorio");
        if (cmd.items() == null || cmd.items().isEmpty()) throw new BusinessException("El pedido debe tener al menos un item");
        for (CrearPedidoItem item : cmd.items()) {
            if (item.productoId() == null) throw new BusinessException("El producto es obligatorio");
            if (item.cantidad() == null || item.cantidad() <= 0) throw new BusinessException("La cantidad debe ser mayor a 0");
        }
        if (cmd.tipoPedido() == TipoPedido.MESA && cmd.mesaId() == null) {
            throw new BusinessException("La mesa es obligatoria para pedidos en mesa");
        }
    }
}
