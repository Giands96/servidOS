package com.servidos.v1.ordering.application.mesa;

import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Número actual de las mesas de los pedidos, para mostrarlo en vez del id. Se lee en una sola
 * consulta por página o cola (sin N+1) e incluye las mesas dadas de baja.
 */
@Service
@RequiredArgsConstructor
public class NumerosDeMesa {

    private final MesaJpaRepository mesaRepository;

    /** mesaId → número; los ids nulos se ignoran y los que no existen no aparecen ({@code get(null)} da null). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> de(Collection<Long> mesaIds, Long restauranteId) {
        var ids = mesaIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return mesaRepository.findByRestauranteIdAndMesaIdIn(restauranteId, ids).stream()
                .collect(Collectors.toMap(MesaJpaEntity::getMesaId, MesaJpaEntity::getNumero));
    }

    /** Número de una mesa, o null si el pedido no tiene mesa. */
    @Transactional(readOnly = true)
    public Integer de(Long mesaId, Long restauranteId) {
        if (mesaId == null) return null;
        return mesaRepository.findByMesaIdAndRestauranteId(mesaId, restauranteId)
                .map(MesaJpaEntity::getNumero)
                .orElse(null);
    }
}
