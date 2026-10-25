package com.servidos.v1.ordering;

import com.servidos.v1.catalog.domain.EstadoProducto;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaEntity;
import com.servidos.v1.catalog.infrastructure.jpa.ProductoJpaRepository;
import com.servidos.v1.identity.domain.EstadoUsuario;
import com.servidos.v1.identity.infrastructure.UsuarioJpaEntity;
import com.servidos.v1.identity.infrastructure.UsuarioJpaRepository;
import com.servidos.v1.identity.infrastructure.security.JwtService;
import com.servidos.v1.ordering.application.mesa.CrearMesaCommand;
import com.servidos.v1.ordering.application.mesa.CrearMesaUseCase;
import com.servidos.v1.ordering.domain.EstadoMesa;
import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.infrastructure.jpa.MesaJpaRepository;
import com.servidos.v1.ordering.infrastructure.jpa.PedidoJpaRepository;
import com.servidos.v1.tenant.domain.EstadoRestaurante;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaEntity;
import com.servidos.v1.tenant.infrastructure.jpa.RestauranteJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El MESERO atiende el salón con HTTP real, Postgres real y la seguridad completa (TenantFilter,
 * SubscriptionFilter y {@code @PreAuthorize}): toma pedidos de mesa, los confirma y los entrega,
 * pero no cobra, no cancela y no maneja delivery. El tenant se siembra con suscripción vigente
 * porque sin ella {@code POST /pedidos} responde 402 antes de llegar a los permisos.
 * <p>
 * A propósito NO es @Transactional: el servidor corre en otro hilo y confirma sus propias
 * transacciones, así que los datos se borran a mano al terminar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PedidoMeseroIntegracionTest {

    /** Tenant sin datos: un MESERO de otro restaurante. */
    static final long OTRO_RESTAURANTE = 999_999_998L;
    static final String SOLO_ENTREGA = "El mesero solo puede marcar como entregado un pedido de mesa listo";
    static final Pattern PEDIDO_ID = Pattern.compile("\"pedidoId\":(\\d+)");

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired RestauranteJpaRepository restauranteRepository;
    @Autowired UsuarioJpaRepository usuarioRepository;
    @Autowired ProductoJpaRepository productoRepository;
    @Autowired CrearMesaUseCase crearMesa;
    @Autowired MesaJpaRepository mesaRepository;
    @Autowired PedidoJpaRepository pedidoRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    Long restauranteId;
    Long usuarioId;
    Long productoId;
    Long mesaId;

    @BeforeEach
    void setUp() {
        var r = new RestauranteJpaEntity();
        r.setSlug("it-mesero-" + System.nanoTime());
        r.setNombre("IT");
        r.setEstado(EstadoRestaurante.ACTIVO);
        restauranteId = restauranteRepository.save(r).getRestauranteId();
        jdbc.update("INSERT INTO suscripcion (restaurante_id, plan_id, monto, moneda, estado, fecha_inicio, fecha_fin, "
                + "created_at, updated_at) SELECT ?, plan_id, 34.90, 'PEN', 'ACTIVA', current_date, current_date + 30, "
                + "now(), now() FROM plan WHERE nombre_plan = 'Estándar'", restauranteId);

        var u = new UsuarioJpaEntity();
        u.setNombre("Mesero");
        u.setApellido("IT");
        u.setEmail("it-mesero-" + System.nanoTime() + "@test.local");
        u.setPasswordHash("x");
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarioId = usuarioRepository.save(u).getUsuarioId();

        var p = new ProductoJpaEntity();
        p.setRestauranteId(restauranteId);
        p.setNombre("Lomo saltado");
        p.setPrecio(new BigDecimal("25.00"));
        p.setEstado(EstadoProducto.DISPONIBLE);
        productoId = productoRepository.save(p).getProductoId();

        mesaId = crearMesa.ejecutar(new CrearMesaCommand(1, 4), restauranteId).getMesa_id();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pago WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM cuenta_mesa WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM detalle_pedido WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM pedido WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM mesa WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM producto WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM suscripcion WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM restaurante WHERE restaurante_id = ?", restauranteId);
        jdbc.update("DELETE FROM usuario WHERE usuario_id = ?", usuarioId);
    }

    // ---- MESERO: lo que sí puede ----

    @Test
    void meseroTomaUnPedidoDeMesaYLaMesaLibreQuedaOcupada() throws Exception {
        assertEquals(EstadoMesa.LIBRE, estadoMesa());

        var resp = send("POST", "/api/v1/pedidos", pedidoJson("MESA", mesaId), "MESERO");

        assertEquals(201, resp.statusCode(), resp.body());
        assertEquals(EstadoPedido.PENDIENTE, estado(pedidoId(resp)));
        assertEquals(EstadoMesa.OCUPADA, estadoMesa());
    }

    @Test
    void meseroTomaElParaLlevarDeUnaMesa() throws Exception {
        var resp = send("POST", "/api/v1/pedidos", pedidoJson("RECOJO", mesaId), "MESERO");

        assertEquals(201, resp.statusCode(), resp.body());
    }

    @Test
    void meseroLeeLosPedidosPorCobrarYLaCuentaDeLaMesa() throws Exception {
        Long pedidoId = crear("MESA", mesaId, "MESERO");

        var listado = send("GET", "/api/v1/pedidos?porCobrar=true", null, "MESERO");
        var cuenta = send("GET", "/api/v1/mesas/" + mesaId + "/cuenta", null, "MESERO");

        assertEquals(200, listado.statusCode(), listado.body());
        assertTrue(listado.body().contains("\"pedidoId\":" + pedidoId), listado.body());
        assertEquals(200, cuenta.statusCode(), cuenta.body());
        assertTrue(cuenta.body().contains("\"pedidoId\":" + pedidoId), cuenta.body());
    }

    @Test
    void meseroConfirmaUnPedidoPendiente() throws Exception {
        Long pedidoId = crear("MESA", mesaId, "MESERO");

        var resp = send("POST", "/api/v1/pedidos/" + pedidoId + "/confirmar", null, "MESERO");

        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(EstadoPedido.EN_PREPARACION, estado(pedidoId));
    }

    @Test
    void meseroEntregaUnPedidoDeMesaListoSinCobrarYLaMesaSigueOcupada() throws Exception {
        Long pedidoId = listo("MESA", mesaId);

        var resp = cambiarEstado(pedidoId, "ENTREGADO", "MESERO");

        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(EstadoPedido.ENTREGADO, estado(pedidoId));
        assertEquals(EstadoMesa.OCUPADA, estadoMesa());
    }

    @Test
    void meseroEntregaUnPedidoDeMesaYaCobradoYLaMesaSeLiberaSola() throws Exception {
        Long pedidoId = listo("MESA", mesaId);
        var pago = send("POST", "/api/v1/pagos", "{\"pedidoId\":" + pedidoId + ",\"metodoPago\":\"YAPE\"}", "CAJERO");
        assertEquals(201, pago.statusCode(), pago.body());

        var resp = cambiarEstado(pedidoId, "ENTREGADO", "MESERO");

        assertEquals(200, resp.statusCode(), resp.body());
        assertEquals(EstadoMesa.LIBRE, estadoMesa());
    }

    // ---- MESERO: lo que no puede ----

    @ParameterizedTest
    @CsvSource({
            "MESA, CANCELADO",
            "DELIVERY, CANCELADO",
            "DELIVERY, EN_ENTREGA",
            "RECOJO, ENTREGADO",
            "RECOJO, CANCELADO"})
    void meseroNoCancelaNiDespachaNiEntregaLoQueNoEsDeMesa(String tipo, String nuevo) throws Exception {
        Long pedidoId = listo(tipo, "MESA".equals(tipo) ? mesaId : null);

        var resp = cambiarEstado(pedidoId, nuevo, "MESERO");

        assertEquals(403, resp.statusCode(), resp.body());
        assertTrue(resp.body().contains(SOLO_ENTREGA), resp.body());
        assertEquals(EstadoPedido.LISTO, estado(pedidoId));
    }

    @Test
    void meseroNoEntregaUnDeliveryEnCamino() throws Exception {
        Long pedidoId = listo("DELIVERY", null);
        assertEquals(200, cambiarEstado(pedidoId, "EN_ENTREGA", "ADMINISTRADOR").statusCode());

        var resp = cambiarEstado(pedidoId, "ENTREGADO", "MESERO");

        assertEquals(403, resp.statusCode(), resp.body());
        assertEquals(EstadoPedido.EN_ENTREGA, estado(pedidoId));
    }

    @Test
    void meseroNoCobraNiLaCuentaNiUnPedido() throws Exception {
        Long pedidoId = crear("MESA", mesaId, "MESERO");

        var cuenta = send("POST", "/api/v1/mesas/" + mesaId + "/cuenta/pagar",
                "{\"metodoPago\":\"YAPE\",\"pedidoIds\":[" + pedidoId + "]}", "MESERO");
        var pago = send("POST", "/api/v1/pagos", "{\"pedidoId\":" + pedidoId + ",\"metodoPago\":\"YAPE\"}", "MESERO");

        assertEquals(403, cuenta.statusCode(), cuenta.body());
        assertEquals(403, pago.statusCode(), pago.body());
        assertEquals(0, contar("pago"));
    }

    @ParameterizedTest
    @CsvSource(value = {"DELIVERY, NULL", "DELIVERY, MESA", "RECOJO, NULL"}, nullValues = "NULL")
    void meseroNoTomaDeliveryNiRecojoSinMesa(String tipo, String mesa) throws Exception {
        var resp = send("POST", "/api/v1/pedidos", pedidoJson(tipo, mesa == null ? null : mesaId), "MESERO");

        assertEquals(403, resp.statusCode(), resp.body());
        assertTrue(resp.body().contains("El mesero solo puede tomar pedidos de mesa o para llevar de una mesa"),
                resp.body());
        assertEquals(0, contar("pedido"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COCINERO", "REPARTIDOR"})
    void losDemasRolesOperativosSiguenSinTomarPedidos(String rol) throws Exception {
        assertEquals(403, send("POST", "/api/v1/pedidos", pedidoJson("MESA", mesaId), rol).statusCode());
        assertEquals(0, contar("pedido"));
    }

    @Test
    void unMeseroDeOtroRestauranteEsRechazado() throws Exception {
        Long pedidoId = listo("MESA", mesaId);
        String ajeno = jwtService.generate(usuarioId, OTRO_RESTAURANTE, "MESERO");

        var entregar = conToken("PATCH", "/api/v1/pedidos/" + pedidoId + "/estado", "{\"estado\":\"ENTREGADO\"}", ajeno);
        var confirmar = conToken("POST", "/api/v1/pedidos/" + pedidoId + "/confirmar", null, ajeno);
        var cuenta = conToken("GET", "/api/v1/mesas/" + mesaId + "/cuenta", null, ajeno);

        assertEquals(400, entregar.statusCode(), entregar.body());
        assertTrue(entregar.body().contains("Pedido no encontrado"), entregar.body());
        assertEquals(400, confirmar.statusCode(), confirmar.body());
        assertEquals(400, cuenta.statusCode(), cuenta.body());
        assertEquals(EstadoPedido.LISTO, estado(pedidoId));
    }

    // ---- Regresión: ADMINISTRADOR y RECEPCION ----

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "RECEPCION"})
    void administradorYRecepcionSiguenCancelandoDespachandoYEntregando(String rol) throws Exception {
        Long cancelado = crear("MESA", mesaId, rol);
        assertEquals(200, cambiarEstado(cancelado, "CANCELADO", rol).statusCode());
        assertEquals(EstadoPedido.CANCELADO, estado(cancelado));

        Long delivery = crear("DELIVERY", null, rol);
        assertEquals(200, send("POST", "/api/v1/pedidos/" + delivery + "/confirmar", null, rol).statusCode());
        assertEquals(200, cambiarEstado(delivery, "LISTO", rol).statusCode());
        assertEquals(200, cambiarEstado(delivery, "EN_ENTREGA", rol).statusCode());
        assertEquals(200, cambiarEstado(delivery, "ENTREGADO", rol).statusCode());
        assertEquals(EstadoPedido.ENTREGADO, estado(delivery));

        Long recojo = crear("RECOJO", null, rol);
        assertEquals(200, cambiarEstado(recojo, "EN_PREPARACION", rol).statusCode());
        assertEquals(200, cambiarEstado(recojo, "LISTO", rol).statusCode());
        assertEquals(200, cambiarEstado(recojo, "ENTREGADO", rol).statusCode());
        assertEquals(EstadoPedido.ENTREGADO, estado(recojo));
    }

    // ---- Helpers ----

    private String pedidoJson(String tipo, Long mesa) {
        return "{\"tipoPedido\":\"" + tipo + "\""
                + (mesa != null ? ",\"mesaId\":" + mesa : "")
                + ("DELIVERY".equals(tipo) ? ",\"repartidorNombre\":\"Juan\"" : "")
                + ",\"items\":[{\"productoId\":" + productoId + ",\"cantidad\":1}]}";
    }

    private Long crear(String tipo, Long mesa, String rol) throws Exception {
        var resp = send("POST", "/api/v1/pedidos", pedidoJson(tipo, mesa), rol);
        assertEquals(201, resp.statusCode(), resp.body());
        return pedidoId(resp);
    }

    /** Un pedido llevado a LISTO por un ADMINISTRADOR. */
    private Long listo(String tipo, Long mesa) throws Exception {
        Long pedidoId = crear(tipo, mesa, "ADMINISTRADOR");
        assertEquals(200, cambiarEstado(pedidoId, "EN_PREPARACION", "ADMINISTRADOR").statusCode());
        assertEquals(200, cambiarEstado(pedidoId, "LISTO", "ADMINISTRADOR").statusCode());
        return pedidoId;
    }

    private HttpResponse<String> cambiarEstado(Long pedidoId, String estado, String rol) throws Exception {
        return send("PATCH", "/api/v1/pedidos/" + pedidoId + "/estado", "{\"estado\":\"" + estado + "\"}", rol);
    }

    private static Long pedidoId(HttpResponse<String> resp) {
        var m = PEDIDO_ID.matcher(resp.body());
        assertTrue(m.find(), resp.body());
        return Long.valueOf(m.group(1));
    }

    private EstadoPedido estado(Long pedidoId) {
        return pedidoRepository.findById(pedidoId).orElseThrow().getEstado();
    }

    private EstadoMesa estadoMesa() {
        return mesaRepository.findById(mesaId).orElseThrow().getEstado();
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE restaurante_id = ?",
                Integer.class, restauranteId);
    }

    private HttpResponse<String> send(String method, String path, String json, String rol)
            throws IOException, InterruptedException {
        return conToken(method, path, json, jwtService.generate(usuarioId, restauranteId, rol));
    }

    private HttpResponse<String> conToken(String method, String path, String json, String bearer)
            throws IOException, InterruptedException {
        var body = json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .method(method, body)
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
