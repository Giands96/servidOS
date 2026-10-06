package com.servidos.v1.tenant.application;

import com.servidos.v1.tenant.application.restaurante.ListarRestaurantesUseCase;
import com.servidos.v1.tenant.application.restaurante.RestauranteConSuscripcion;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarRestaurantesUseCaseTest {

    @Mock RestauranteJpaRepository restauranteRepository;

    @InjectMocks ListarRestaurantesUseCase useCase;

    @Test
    void delegaPaginadoAlRepositorio() {
        var pageable = PageRequest.of(0, 10);
        var fila = new RestauranteConSuscripcion(1L, "demo", "Demo", null,
                EstadoRestaurante.ACTIVO, null, null, null, null, null, null);
        when(restauranteRepository.listarConSuscripcionActual(eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(fila)));
        var page = useCase.listar(pageable);
        assertEquals(1, page.getTotalElements());
        assertEquals("demo", page.getContent().get(0).slug());
        verify(restauranteRepository).listarConSuscripcionActual(eq(pageable));
    }
}
