package com.servidos.v1.payment.infrastructure.jpa;

import com.servidos.v1.payment.domain.MetodoPago;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * El cobro conjunto de la cuenta de una mesa. Los pagos de la cuenta (uno por pedido) apuntan acá
 * con {@code pago.cuenta_id}; el vuelto vive en esta fila y no en cada pago.
 */
@Entity
@Table(name = "cuenta_mesa")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuentaMesaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cuenta_id")
    private Long cuentaId;

    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;

    @Column(name = "mesa_id", nullable = false)
    private Long mesaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago", nullable = false, length = 20)
    private MetodoPago metodoPago;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "monto_entregado", precision = 10, scale = 2)
    private BigDecimal montoEntregado;

    @Column(name = "vuelto", precision = 10, scale = 2)
    private BigDecimal vuelto;

    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
