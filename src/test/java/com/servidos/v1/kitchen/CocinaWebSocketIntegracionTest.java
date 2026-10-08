package com.servidos.v1.kitchen;

import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.kitchen.api.dto.CocinaEventoMessage;
import com.servidos.v1.kitchen.application.cocina.GestionarColaCocinaUseCase;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaEntity;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * De punta a punta: servidor real, cliente STOMP real y Postgres. Los datos se commitean
 * (el aviso sale después del commit), así que se borran a mano al terminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CocinaWebSocketIntegracionTest {

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired GestionarColaCocinaUseCase cocina;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired PedidoJpaRepository pedidoRepository;

    WebSocketStompClient client;
    Long restauranteId;
    Long usuarioId;
    Long pedidoId;

    @BeforeEach
    void setUp() {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());

        var r = new RestauranteJpaEntity();
        r.setSlug("it-ws-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();

        var u = new UsuarioJpaEntity();
        u.setNombre("Cocina");
        u.setApellido("IT");
        u.setEmail("it-ws-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        var p = new PedidoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setUsuarioId(usuarioId);
        p.setTipoPedido(TipoPedido.RECOJO);
        p.setEstado(EstadoPedido.EN_PREPARACION);
        p.setTotal(new BigDecimal("10.00"));
        pedidoId = pedidoRepository.save(p).getPedidoId();
    }

    @AfterEach
    void limpiar() {
        client.stop();
        pedidoRepository.deleteById(pedidoId);
        usuarioRepository.deleteById(usuarioId);
        restauranteRepository.deleteById(restauranteId);
    }

    private StompSession conectar(String token, StompSessionHandlerAdapter handler) throws Exception {
        var connect = new StompHeaders();
        if (token != null) connect.add("Authorization", "Bearer " + token);
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect, handler)
                .get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<CocinaEventoMessage> suscribir(StompSession session, String destino) {
        BlockingQueue<CocinaEventoMessage> recibidos = new LinkedBlockingQueue<>();
        session.subscribe(destino, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return CocinaEventoMessage.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                recibidos.add((CocinaEventoMessage) payload);
            }
        });
        return recibidos;
    }

    @Test
    void marcarListoAvisaAlTableroDelRestaurante() throws Exception {
        var session = conectar(jwtService.generate(usuarioId, restauranteId, "COCINERO"),
                new StompSessionHandlerAdapter() { });
        var recibidos = suscribir(session, "/topic/restaurantes/" + restauranteId + "/cocina");
        Thread.sleep(300); // que el SUBSCRIBE llegue al broker antes del aviso

        cocina.marcarListo(pedidoId, restauranteId);

        var msg = recibidos.poll(5, TimeUnit.SECONDS);
        assertNotNull(msg, "no llegó el aviso por WebSocket");
        assertEquals(new CocinaEventoMessage(pedidoId, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO), msg);
    }

    @Test
    void suscribirseAlTableroDeOtroRestauranteCortaLaSesionYNoRecibeNada() throws Exception {
        var error = new CompletableFuture<Void>();
        var session = conectar(jwtService.generate(usuarioId, restauranteId + 1000, "COCINERO"),
                new StompSessionHandlerAdapter() {
                    @Override public void handleFrame(StompHeaders headers, Object payload) { error.complete(null); }
                    @Override public void handleTransportError(StompSession s, Throwable ex) { error.complete(null); }
                });
        var recibidos = suscribir(session, "/topic/restaurantes/" + restauranteId + "/cocina");

        error.get(5, TimeUnit.SECONDS);
        cocina.marcarListo(pedidoId, restauranteId);
        assertNull(recibidos.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void conectarSinTokenSeRechaza() {
        assertThrows(Exception.class, () -> conectar(null, new StompSessionHandlerAdapter() { }));
    }
}
