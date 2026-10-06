package com.servidos.v1.tenant.domain;

import com.servidos.v1.shared.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Restaurante {
    private Long restaurante_id;
    private String slug;
    private String nombre;
    private String direccion;
    private EstadoRestaurante estado;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;

    public static Restaurante crear(String slug, String nombre, String direccion) {
        if (slug == null || slug.trim().isEmpty()) throw new BusinessException("El slug es obligatorio");
        String s = slug.trim().toLowerCase();
        if (s.length() < 3 || s.length() > 100) throw new BusinessException("El slug debe tener entre 3 y 100 caracteres");
        if (!s.matches("^[a-z0-9-]+$")) throw new BusinessException("El slug solo puede contener letras minúsculas, números y guiones");
        if (nombre == null || nombre.trim().isEmpty()) throw new BusinessException("El nombre es obligatorio");
        if (nombre.trim().length() > 150) throw new BusinessException("El nombre no puede tener más de 150 caracteres");
        return Restaurante.builder().slug(s).nombre(nombre.trim()).direccion(direccion).estado(EstadoRestaurante.ACTIVO).build();
    }

    /**
     * Cambia el estado del restaurante. Es la palanca de acceso del tenant: en
     * {@code INACTIVO} el {@code SubscriptionFilter} frena las escrituras con 402 pero
     * deja pasar las lecturas, para que el cliente conserve acceso de solo lectura a
     * sus datos mientras regulariza la suscripción.
     *
     * <p>La reactivación es un acto de la plataforma (SUPERADMIN), nunca del tenant:
     * el cobro se resuelve fuera del sistema, así que el sistema no tiene cómo
     * verificar que el pago ocurrió y no puede auto-reactivar.
     */
    public void cambiarEstado(EstadoRestaurante nuevo) {
        if (nuevo == null) throw new BusinessException("El estado es obligatorio");
        this.estado = nuevo;
    }
}
