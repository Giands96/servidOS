package com.servidos.v1.identity.application.auth;

public record LoginCommand(String email, String password, String clientIp) {
    public LoginCommand(String email, String password) {
        this(email, password, "desconocida");
    }
}
