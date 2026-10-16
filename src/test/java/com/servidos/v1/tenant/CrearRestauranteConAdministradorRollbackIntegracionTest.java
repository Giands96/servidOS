package com.servidos.v1.tenant;

import com.servidos.v1.identity.domain.event.UsuarioCreadoEvent;
import com.servidos.v1.shared.event.EventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * El alta del dueño es el último paso persistente de la transacción. Si algo falla ahí, tiene que
 * deshacerse todo lo anterior: restaurante, suscripción y usuario. Se fuerza el fallo en la
 * publicación del evento del usuario, que ocurre ya con todo escrito y con el flush hecho.
 */
class CrearRestauranteConAdministradorRollbackIntegracionTest extends CrearRestauranteConAdministradorTestSupport {

    @MockitoBean EventPublisher eventPublisher;

    @Test
    void siFallaElAltaDelAdministradorSeDeshaceElRestauranteCompleto() throws Exception {
        doThrow(new IllegalStateException("fallo simulado")).when(eventPublisher).publish(any(UsuarioCreadoEvent.class));
        String slug = nuevoSlug();
        String email = nuevoEmail();

        var resp = crearRestaurante(bodyCrearRestaurante(slug, email, PASSWORD));

        assertEquals(500, resp.statusCode(), resp.body());
        assertFalse(resp.body().contains(PASSWORD));
        assertFalse(restauranteRepository.existsBySlug(slug), "el restaurante debe haberse revertido");
        assertFalse(usuarioRepository.existsByEmail(email), "el usuario debe haberse revertido");
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM suscripcion s JOIN restaurante r ON r.restaurante_id = s.restaurante_id "
                        + "WHERE r.slug = ?", Integer.class, slug));
    }
}
