package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.usuario.CrearUsuarioCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioUseCase;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.RolRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioRestauranteJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.event.EventPublisher;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrearUsuarioSeguridadTest {

    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock UsuarioRestauranteJpaRepository usuarioRestauranteRepository;
    @Mock RestauranteJpaRepository restauranteRepository;
    @Mock RolRestauranteJpaRepository rolRepository;
    @Mock UsuarioMapper usuarioMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EventPublisher eventPublisher;

    CrearUsuarioUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearUsuarioUseCase(
                usuarioRepository, usuarioRestauranteRepository, restauranteRepository,
                rolRepository, usuarioMapper, passwordEncoder, eventPublisher);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static CrearUsuarioCommand cmdBase() {
        return new CrearUsuarioCommand("Ana", "Paz", "ana@demo.pe", "secreta123", 10L);
    }

    @Test
    void crossTenantEs403No400() {
        var membresia = new UsuarioRestauranteJpaEntity();
        membresia.setUsuarioId(1L);
        membresia.setRestauranteId(100L);
        when(usuarioRestauranteRepository.findById(1L)).thenReturn(Optional.of(membresia));

        var ex = assertThrows(ForbiddenException.class,
                () -> useCase.ejecutar(cmdBase(), 999L));
        assertEquals("Operación cross-tenant rechazada", ex.getMessage());
    }

    @Test
    void administradorNoPuedeCrearOtroAdministrador() {
        var membresia = new UsuarioRestauranteJpaEntity();
        membresia.setUsuarioId(1L);
        membresia.setRestauranteId(100L);
        when(usuarioRestauranteRepository.findById(1L)).thenReturn(Optional.of(membresia));
        when(usuarioRepository.existsByEmail("ana@demo.pe")).thenReturn(false);
        when(restauranteRepository.existsById(100L)).thenReturn(true);
        var rol = new RolRestauranteJpaEntity();
        rol.setRolRestauranteId(10L);
        rol.setNombre("ADMINISTRADOR");
        rol.setEstado("ACTIVO");
        when(rolRepository.findById(10L)).thenReturn(Optional.of(rol));

        assertThrows(ForbiddenException.class, () -> useCase.ejecutar(cmdBase(), 100L));
    }

    @Test
    void rolInactivoSeRechaza() {
        var membresia = new UsuarioRestauranteJpaEntity();
        membresia.setUsuarioId(1L);
        membresia.setRestauranteId(100L);
        when(usuarioRestauranteRepository.findById(1L)).thenReturn(Optional.of(membresia));
        when(usuarioRepository.existsByEmail("ana@demo.pe")).thenReturn(false);
        when(restauranteRepository.existsById(100L)).thenReturn(true);
        var rol = new RolRestauranteJpaEntity();
        rol.setRolRestauranteId(10L);
        rol.setNombre("RECEPCION");
        rol.setEstado("INACTIVO");
        when(rolRepository.findById(10L)).thenReturn(Optional.of(rol));

        assertThrows(BusinessException.class, () -> useCase.ejecutar(cmdBase(), 100L));
    }
}
