package com.servidos.v1.ordering.infrastructure.jpa;

import com.servidos.v1.ordering.domain.EstadoPedido;
import com.servidos.v1.ordering.domain.TipoPedido;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedido")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PedidoJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pedido_id")
    private Long pedidoId;
    @Column(name = "restaurante_id", nullable = false)
    private Long restauranteId;
    @Column(name = "usuario_id")
    private Long usuarioId;
    @Column(name = "mesa_id")
    private Long mesaId;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pedido", nullable = false, length = 20)
    private TipoPedido tipoPedido;
    @Column(name = "observacion", length = 500)
    private String observacion;
    @Column(name = "repartidor_nombre", length = 100)
    private String repartidorNombre;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPedido estado;
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { LocalDateTime now = LocalDateTime.now(); this.createdAt = now; this.updatedAt = now; }
    @PreUpdate protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
