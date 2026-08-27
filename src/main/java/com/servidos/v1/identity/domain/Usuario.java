package com.servidos.v1.identity.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    Long usuario_id;

    @Column(name=" nombre", nullable = false)
    String nombre;

    @Column(name=" apellido", nullable = false)
    String apellido;

    @Column(name="email", nullable = false, unique = true)
    String email;
    @Column(name="password_hash", nullable = false)
    String password_hash;
    @Column(name="ultimo_acceso")
    LocalDateTime ultimo_acceso;

    @Enumerated(EnumType.STRING)
    @Column(name="estado")
    EstadoUsuario estado;

    @Column(name="created_at",  nullable = false, updatable = false)
    LocalDateTime created_at;

    @Column(name="updated_at", nullable = false)
    LocalDateTime updated_at;

    @PrePersist
    protected void onCreate() {
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now(); // Al crear, ambas fechas son iguales
    }

    @PreUpdate
    protected void onUpdate() {
        this.updated_at = LocalDateTime.now(); // Al editar, solo cambia esta fecha
    }



}
