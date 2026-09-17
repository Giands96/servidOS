package com.servidos.v1.identity.api;

import com.servidos.v1.identity.application.rol.AsignarRolPlataformaUseCase;
import com.servidos.v1.identity.application.rol.AsignarRolRestauranteUseCase;
import com.servidos.v1.identity.application.usuario.CrearUsuarioUseCase;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioCommand;
import com.servidos.v1.identity.application.usuario.EliminarUsuarioUseCase;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerEliminarTest {

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
    void conTenantDelegaYDa204() {
        TenantContext.setRestauranteId(5L);

        var resp = controller.eliminar(2L);

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        verify(eliminarUsuarioUseCase).ejecutar(eq(new EliminarUsuarioCommand(2L)), eq(5L));
    }

    @Test
    void sinTenantEs400YSinDelegar() {
        TenantContext.clear();

        assertThrows(BusinessException.class, () -> controller.eliminar(2L));
        verifyNoInteractions(eliminarUsuarioUseCase);
    }
}
