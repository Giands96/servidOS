package com.servidos.v1.identity.application.usuario;

public record CrearUsuarioPlataformaCommand(String nombre,
                                            String apellido,
                                            String email,
                                            String password,
                                            Long rolPlataformaId) {}
