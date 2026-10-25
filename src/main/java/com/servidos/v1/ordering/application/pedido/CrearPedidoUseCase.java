package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.ProductCatalogPort;
import com.servidos.v1.ordering.application.mesa.AsignarMesaAPedidoUseCase;
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
import com.servidos.v1.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrearPedidoUseCase {
    private static final String MESERO = "MESERO";
    /** Roles que toman cualquier tipo de pedido. Cualquier otro rol (o ninguno) se rechaza: la regla no falla abierta. */
    private static final Set<String> ROLES_TODO_TIPO = Set.of("ADMINISTRADOR", "RECEPCION");

    private final PedidoJpaRepository pedidoRepository;
    private final DetallePedidoJpaRepository detalleRepository;
    private final ProductCatalogPort productoCatalog;
    private final PedidoMapper pedidoMapper;
    private final DetallePedidoMapper detalleMapper;
    private final EventPublisher eventPublisher;
    private final AsignarMesaAPedidoUseCase asignarMesa;

    @Transactional
    public Pedido ejecutar(CrearPedidoCommand cmd, Long restauranteId, Long usuarioId) {
        return crear(cmd, restauranteId, usuarioId, false);
    }

    /**
     * El rol sale del JWT ({@code CurrentUser}), nunca del JSON. El MESERO atiende el salón: toma pedidos
     * MESA y el RECOJO "para llevar" de una mesa, pero no DELIVERY ni RECOJO sin mesa. ADMINISTRADOR y
     * RECEPCION toman cualquier tipo; un rol nulo o desconocido recibe 403.
     */
    @Transactional
    public Pedido ejecutar(CrearPedidoCommand cmd, Long restauranteId, Long usuarioId, String rol) {
        boolean esMesero = MESERO.equalsIgnoreCase(rol);
        if (!esMesero && (rol == null || !ROLES_TODO_TIPO.contains(rol.toUpperCase()))) {
            throw new ForbiddenException("Rol sin permiso para tomar pedidos");
        }
        return crear(cmd, restauranteId, usuarioId, esMesero);
    }

    private Pedido crear(CrearPedidoCommand cmd, Long restauranteId, Long usuarioId, boolean esMesero) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        // pedido.usuario_id es NOT NULL: registra quién tomó el pedido.
        if (usuarioId == null) throw new BusinessException("Usuario no identificado");
        validar(cmd);
        if (esMesero) exigirPedidoDeSalon(cmd);
        // Valida la mesa y, si el pedido es de mesa, la ocupa (con lock) antes de guardar el pedido.
        asignarMesa.ejecutar(cmd.mesaId(), cmd.tipoPedido(), restauranteId);

        Pedido pedidoDomain = Pedido.crear(restauranteId,
                usuarioId,
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
            var producto = productoCatalog.findInfoByIdAndRestaurante(item.productoId(), restauranteId)
                    .orElseThrow(() -> new BusinessException("Producto no encontrado: " + item.productoId()));
            if (!producto.disponible()) {
                throw new BusinessException("Producto no disponible: " + item.productoId());
            }
            DetallePedido detalle = DetallePedido.crear(
                    restauranteId,
                    savedPedido.getPedidoId(),
                    item.productoId(),
                    item.cantidad(),
                    producto.nombre(),
                    producto.precio(),
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

    private void exigirPedidoDeSalon(CrearPedidoCommand cmd) {
        boolean deSalon = cmd.tipoPedido() == TipoPedido.MESA
                || (cmd.tipoPedido() == TipoPedido.RECOJO && cmd.mesaId() != null);
        if (!deSalon) {
            throw new ForbiddenException("El mesero solo puede tomar pedidos de mesa o para llevar de una mesa");
        }
    }

    private void validar(CrearPedidoCommand cmd) {
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
