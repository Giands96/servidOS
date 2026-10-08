package com.servidos.v1.kitchen.infrastructure;

import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.kitchen.api.CocinaWebSocketController;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;

/**
 * Seguridad del canal STOMP. El handshake HTTP de {@code /ws} es público porque el
 * navegador no puede mandar el header Authorization en un WebSocket; la autenticación
 * ocurre en el frame CONNECT, que trae {@code Authorization: Bearer <jwt>}.
 * <ul>
 *   <li>CONNECT: token válido, con restaurante y con un rol que ve la cocina.</li>
 *   <li>SUBSCRIBE: solo al tópico de cocina del restaurante del token.</li>
 *   <li>SEND: rechazado; el canal es solo de servidor a cliente.</li>
 * </ul>
 * El token se valida al conectar: si vence, la conexión sigue hasta que el cliente
 * reconecte (con el token renovado).
 */
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    static final Set<String> ROLES_COCINA = Set.of("ADMINISTRADOR", "RECEPCION", "COCINERO");

    private final JwtService jwtService;

    /** Usuario de la sesión STOMP; el nombre es el id de usuario. */
    public record UsuarioStomp(Long usuarioId, Long restauranteId, String rol) implements Principal {
        @Override
        public String getName() {
            return String.valueOf(usuarioId);
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(autenticar(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> autorizarSuscripcion(accessor.getUser(), accessor.getDestination());
            case SEND -> throw new MessageDeliveryException("El canal no acepta mensajes del cliente");
            default -> { }
        }
        return message;
    }

    private UsuarioStomp autenticar(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Falta el token");
        }
        String jwt = authorization.substring(7);
        if (!jwtService.isTokenValid(jwt)) {
            throw new MessageDeliveryException("Credenciales inválidas");
        }
        Long restauranteId = jwtService.extractRestauranteId(jwt);
        String rol = jwtService.extractRol(jwt);
        if (restauranteId == null || !ROLES_COCINA.contains(rol)) {
            throw new MessageDeliveryException("Sin permiso");
        }
        return new UsuarioStomp(jwtService.extractUsuarioId(jwt), restauranteId, rol);
    }

    private void autorizarSuscripcion(Principal user, String destino) {
        if (!(user instanceof UsuarioStomp usuario)) {
            throw new MessageDeliveryException("Sin sesión");
        }
        if (!CocinaWebSocketController.topico(usuario.restauranteId()).equals(destino)) {
            throw new MessageDeliveryException("Sin permiso para " + destino);
        }
    }
}
