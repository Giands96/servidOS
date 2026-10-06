package com.servidos.v1.tenant.application.restaurante;

import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListarRestaurantesUseCase {

    private final RestauranteJpaRepository restauranteRepository;

    @Transactional(readOnly = true)
    public Page<RestauranteConSuscripcion> listar(Pageable pageable) {
        return restauranteRepository.listarConSuscripcionActual(pageable);
    }
}
