package com.servidos.v1.identity.application.auth;

public record LoginSession(String accessToken, String refreshToken) {}
