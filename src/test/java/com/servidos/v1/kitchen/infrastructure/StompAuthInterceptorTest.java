package com.servidos.v1.kitchen.infrastructure;

import com.servidos.v1.identity.infrastructure.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

    @Mock JwtService jwtService;

    @InjectMocks StompAuthInterceptor interceptor;

    private static final StompAuthInterceptor.UsuarioStomp COCINERO_REST_1 =
            new StompAuthInterceptor.UsuarioStomp(5L, 1L, "COCINERO");

    private Message<byte[]> frame(StompCommand command, String token, String destino, Principal user) {
        var accessor = StompHeaderAccessor.create(command);
        if (token != null) accessor.setNativeHeader("Authorization", "Bearer " + token);
        if (destino != null) accessor.setDestination(destino);
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void dadoToken(Long restauranteId, String rol) {
        when(jwtService.isTokenValid("tok")).thenReturn(true);
        when(jwtService.extractRestauranteId("tok")).thenReturn(restauranteId);
        when(jwtService.extractRol("tok")).thenReturn(rol);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "RECEPCION", "COCINERO"})
    void connectConRolDeCocinaQuedaAutenticado(String rol) {
        dadoToken(1L, rol);
        when(jwtService.extractUsuarioId("tok")).thenReturn(5L);

        var msg = interceptor.preSend(frame(StompCommand.CONNECT, "tok", null, null), null);

        var user = StompHeaderAccessor.wrap(msg).getUser();
        assertEquals(new StompAuthInterceptor.UsuarioStomp(5L, 1L, rol), user);
    }

    @Test
    void connectSinTokenFalla() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null));
    }

    @Test
    void connectConTokenInvalidoFalla() {
        when(jwtService.isTokenValid("tok")).thenReturn(false);
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "tok", null, null), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MESERO", "CAJERO", "REPARTIDOR"})
    void connectConRolSinCocinaFalla(String rol) {
        dadoToken(1L, rol);
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "tok", null, null), null));
    }

    /** SUPERADMIN no tiene restaurante: no hay tablero que mirar. */
    @Test
    void connectSinRestauranteFalla() {
        dadoToken(null, "ADMINISTRADOR");
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "tok", null, null), null));
    }

    @Test
    void subscribeAlTopicoPropioPasa() {
        assertDoesNotThrow(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/restaurantes/1/cocina", COCINERO_REST_1), null));
    }

    @Test
    void subscribeAlTopicoDeOtroRestauranteFalla() {
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/restaurantes/2/cocina", COCINERO_REST_1), null));
    }

    @Test
    void subscribeSinSesionFalla() {
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/restaurantes/1/cocina", null), null));
    }

    @Test
    void sendDelClienteFalla() {
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SEND, null, "/app/algo", COCINERO_REST_1), null));
    }
}
