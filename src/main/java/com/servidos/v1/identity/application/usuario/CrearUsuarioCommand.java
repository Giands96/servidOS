package com.servidos.v1.identity.application.usuario;

public record CrearUsuarioCommand(String nombre,
                                  String apellido,
                                  String email,
                                  String password,
                                  Long rolRestauranteId) {}
