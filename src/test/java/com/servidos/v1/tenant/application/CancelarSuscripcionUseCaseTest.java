package com.servidos.v1.tenant.application;

import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.shared.exception.BusinessException;
import com.servidos.v1.shared.exception.UnauthorizedException;
import com.servidos.v1.shared.security.CurrentUser;
import com.servidos.v1.tenant.application.suscripcion.GestionarSuscripcionUseCase;
import com.servidos.v1.tenant.domain.Suscripcion.EstadoSuscripcion;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.SuscripcionJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarSuscripcionUseCaseTest {

    @Mock SuscripcionJpaRepository suscripcionRepository;
    @Mock UsuarioJpaRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks GestionarSuscripcionUseCase useCase;

    @AfterEach
    void limpiar() {
        CurrentUser.clear();
    }

    private void dadoUsuario(boolean passwordCorrecta) {
        CurrentUser.setCurrentUser(42L);
        var usuario = new UsuarioJpaEntity();
        usuario.setPasswordHash("hash");
        when(usuarioRepository.findById(42L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("secreto", "hash")).thenReturn(passwordCorrecta);
    }

    @Test
    void conPasswordCorrectaCancela() {
        dadoUsuario(true);
        var actual = new SuscripcionJpaEntity();
        actual.setEstado(EstadoSuscripcion.ACTIVA);
        when(suscripcionRepository.findTopByRestauranteIdOrderByCreatedAtDescSuscripcionIdDesc(7L))
                .thenReturn(Optional.of(actual));

        useCase.cancelar(7L, "secreto");

        assertEquals(EstadoSuscripcion.CANCELADA, actual.getEstado());
        verify(suscripcionRepository).save(actual);
    }

    @Test
    void conPasswordIncorrectaDa401YNoCancela() {
        dadoUsuario(false);

        assertThrows(UnauthorizedException.class, () -> useCase.cancelar(7L, "secreto"));

        verify(suscripcionRepository, never()).save(any());
    }

    @Test
    void sinPasswordDa400YNoConsultaNada() {
        assertThrows(BusinessException.class, () -> useCase.cancelar(7L, ""));

        verify(usuarioRepository, never()).findById(any());
        verify(suscripcionRepository, never()).save(any());
    }
}
