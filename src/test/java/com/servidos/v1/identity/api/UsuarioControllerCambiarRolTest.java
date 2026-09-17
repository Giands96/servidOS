package com.servidos.v1.identity.api;

import com.servidos.v1.identity.api.dto.CambiarRolRequest;
import com.servidos.v1.identity.application.rol.AsignarRolPlataformaUseCase;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteUseCase;
import com.servidos.v1.identity.application.usuario.CrearUsuarioUseCase;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioUseCase;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerCambiarRolTest {

    @Mock
    CrearUsuarioUseCase crearUsuarioUseCase;
    @Mock
    AsignarRolRestauranteUseCase rolRestaurante;
    @Mock
    AsignarRolPlataformaUseCase rolPlataforma;
    @Mock
    EliminarUsuarioUseCase eliminarUsuarioUseCase;

    UsuarioController controller;

    @BeforeEach
    void setUp() {
        controller = new UsuarioController(crearUsuarioUseCase, rolRestaurante, rolPlataforma, eliminarUsuarioUseCase);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    @Test
    void conTenantDelegaARestauranteYDa204() {
        TenantContext.setRestauranteId(5L);

        var resp = controller.cambiarRol(2L, new CambiarRolRequest(20L));

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(rolRestaurante).ejecutar(any(), eq(5L));
        verifyNoInteractions(rolPlataforma);
    }

    @Test
    void sinTenantDelegaAPlataformaYDa204() {
        TenantContext.clear();

        var resp = controller.cambiarRol(2L, new CambiarRolRequest(20L));

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(rolPlataforma).ejecutar(any());
        verifyNoInteractions(rolRestaurante);
    }
}
