package com.servidos.v1.identity.application;

import com.servidos.v1.identity.application.usuario.CrearUsuarioPlataformaCommand;
import com.servidos.v1.identity.application.usuario.CrearUsuarioPlataformaUseCase;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.RolPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioPlataformaJpaRepository;
import com.servidos.v1.identity.infrastructure.mapper.UsuarioMapper;
import com.servidos.v1.shared.exception.ConflictException;
import com.servidos.v1.shared.exception.ForbiddenException;
import com.servidos.v1.shared.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrearUsuarioPlataformaUseCaseTest {

    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock UsuarioPlataformaJpaRepository usuarioPlataformaRepository;
    @Mock RolPlataformaJpaRepository rolRepository;
    @Mock PasswordEncoder passwordEncoder;

    CrearUsuarioPlataformaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CrearUsuarioPlataformaUseCase(usuarioRepository, usuarioPlataformaRepository,
                rolRepository, new UsuarioMapper(), passwordEncoder);
        CurrentUser.setCurrentUser(1L);
        CurrentUser.setRole("SUPERADMIN");
    }

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private static CrearUsuarioPlataformaCommand cmd(Long rolId) {
        return new CrearUsuarioPlataformaCommand("Socia", "Pérez", "Socia@Mail.com", "secreto123", rolId);
    }

    private void dadoActorSuperadmin() {
        when(usuarioPlataformaRepository.findById(1L)).thenReturn(Optional.of(new UsuarioPlataformaJpaEntity()));
    }

    private void dadoRol(Long id, String nombre) {
        var r = new RolPlataformaJpaEntity();
        r.setRolPlataformaId(id);
        r.setNombre(nombre);
        r.setEstado("ACTIVO");
        when(rolRepository.findById(id)).thenReturn(Optional.of(r));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MODERADOR"})
    void superadminCreaAdminOModerador(String rol) {
        dadoActorSuperadmin();
        dadoRol(20L, rol);
        when(usuarioRepository.existsByEmail("socia@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("secreto123")).thenReturn("hash");
        when(usuarioRepository.saveAndFlush(any())).thenAnswer(i -> {
            var e = (com.servidos.v1.identity.infrastructure.UsuarioJpaEntity) i.getArgument(0);
            e.setUsuarioId(7L);
            return e;
        });

        var creado = useCase.ejecutar(cmd(20L));

        assertEquals(7L, creado.getUsuario_id());
        assertEquals("socia@mail.com", creado.getEmail());
        assertNull(creado.getPassword_hash());
        var membresia = ArgumentCaptor.forClass(UsuarioPlataformaJpaEntity.class);
        verify(usuarioPlataformaRepository).save(membresia.capture());
        assertEquals(7L, membresia.getValue().getUsuarioId());
        assertEquals(20L, membresia.getValue().getRolPlataformaId());
        assertEquals(EstadoUsuario.ACTIVO, membresia.getValue().getEstado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MODERADOR"})
    void soloSuperadminCrea(String rolActor) {
        CurrentUser.setRole(rolActor);
        assertThrows(ForbiddenException.class, () -> useCase.ejecutar(cmd(20L)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void noSeCreaOtroSuperadmin() {
        dadoActorSuperadmin();
        dadoRol(10L, "SUPERADMIN");
        assertThrows(ForbiddenException.class, () -> useCase.ejecutar(cmd(10L)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void emailRepetidoEs409() {
        dadoActorSuperadmin();
        dadoRol(20L, "ADMIN");
        when(usuarioRepository.existsByEmail("socia@mail.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> useCase.ejecutar(cmd(20L)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }
}
