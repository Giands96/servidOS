package com.servidos.v1.identity.infrastructure;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {

    private String header = "Bearer ";

    @Value("${servidos.auth.secret}")
    private String secretKey;

    @Value("${servidos.auth.issuer}")
    private String issuer;

    @Value("${servidos.auth.audience}")
    private String audience;

    @Value("${servidos.auth.access-ttl}")
    private Duration accessTtl;

    @Value("${servidos.auth.refresh-absolute-ttl}")
    private Duration refreshTtl;


    //* PASOS PARA CREAR EL CUERPO DEL TOKEN

    //* 1. Crear un método para generar el token de acceso y el token de actualización.
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generate(Long usuarioId, Long restauranteId, Long rolId){
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + accessTtl.toMillis());

        return Jwts.builder()
                .subject(usuarioId.toString())
                .claim("restauranteId", restauranteId)
                .claim("rolId", rolId)
                .claim("aud", audience)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .build().parseSignedClaims(token).getPayload();
    }

    public Long extractRestauranteId(String token) {
        return extractAllClaims(token).get("restauranteId", Long.class);
    }

    public Long extractUsuarioId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}