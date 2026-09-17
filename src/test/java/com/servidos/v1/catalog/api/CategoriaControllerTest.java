package com.servidos.v1.catalog.api;

import com.servidos.v1.catalog.api.dto.CrearCategoriaRequest;
import com.servidos.v1.catalog.application.categoria.GestionarCategoriaUseCase;
import com.servidos.v1.catalog.application.categoria.ListarCategoriasUseCase;
import com.servidos.v1.catalog.domain.Categoria;
import com.servidos.v1.catalog.domain.EstadoCategoria;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CategoriaControllerTest {

    @Mock GestionarCategoriaUseCase gestionarCategoriaUseCase;
    @Mock ListarCategoriasUseCase listarCategoriasUseCase;

    CategoriaController controller;

    @BeforeEach
    void setUp() {
        controller = new CategoriaController(gestionarCategoriaUseCase, listarCategoriasUseCase);
        TenantContext.setRestauranteId(100L);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void crearDelegaYDa201() {
        when(gestionarCategoriaUseCase.ejecutar(any(), eq(100L))).thenReturn(Categoria.builder()
                .categoria_id(7L).restaurante_id(100L)
                .nombre("Criollos").estado(EstadoCategoria.HABILITADO).build());

        var resp = controller.crear(new CrearCategoriaRequest("Criollos", EstadoCategoria.HABILITADO));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(7L, resp.getBody().categoriaId());
    }

    @Test
    void listarDa200() {
        var pageable = PageRequest.of(0, 20);
        when(listarCategoriasUseCase.listar(100L, pageable)).thenReturn(new PageImpl<>(List.of(Categoria.builder()
                .categoria_id(7L).restaurante_id(100L)
                .nombre("Criollos").estado(EstadoCategoria.HABILITADO).build())));

        var resp = controller.listar(pageable);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getTotalElements());
    }
}
