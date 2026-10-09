package com.servidos.v1.ordering.application.pedido;

import com.servidos.v1.ordering.application.PedidoPagoPort;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.Pedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.ordering.infrastructure.mapper.PedidoMapper;
import com.servidos.v1.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Pedidos del restaurante, del más nuevo al más viejo, con el estado de su pago.
 * Sirve a caja (por cobrar), recepción y el historial del día.
 */
@Service
@RequiredArgsConstructor
public class ListarPedidosUseCase {

    static final int TAMANO_MAXIMO = 100;

    private final PedidoJpaRepository pedidoRepository;
    private final PedidoMapper pedidoMapper;
    private final PedidoPagoPort pagoPort;

    /** {@code estadoPago} es null si el pedido no tiene pago. */
    public record PedidoListado(Pedido pedido, String estadoPago) {}

    /**
     * @param estado    solo pedidos en ese estado (opcional)
     * @param fecha     solo pedidos creados ese día, hora de Lima (opcional)
     * @param porCobrar solo pedidos no cancelados que nunca se cobraron
     */
    @Transactional(readOnly = true)
    public Page<PedidoListado> listar(Long restauranteId, EstadoPedido estado, LocalDate fecha,
                                      boolean porCobrar, int pagina, int tamano) {
        if (restauranteId == null) throw new BusinessException("Restaurante no identificado");
        if (pagina < 0) throw new BusinessException("La página no puede ser negativa");
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new BusinessException("El tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }

        Page<PedidoJpaEntity> page = pedidoRepository.listar(restauranteId,
                estado == null ? null : estado.name(),
                fecha == null ? null : fecha.atStartOfDay(),
                fecha == null ? null : fecha.plusDays(1).atStartOfDay(),
                porCobrar, PageRequest.of(pagina, tamano));

        var estadosDePago = pagoPort.estadosDePago(
                page.getContent().stream().map(PedidoJpaEntity::getPedidoId).toList(), restauranteId);
        return page.map(e -> new PedidoListado(pedidoMapper.toDomain(e), estadosDePago.get(e.getPedidoId())));
    }
}
