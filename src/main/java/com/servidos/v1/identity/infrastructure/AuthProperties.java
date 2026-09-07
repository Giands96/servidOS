package com.servidos.v1.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "servidos.auth")
public record AuthProperties (
        String secret,
        String issuer,
        String audience,
        Duration accessTtl,
        Duration refreshAbsoluteTtl
){

    public AuthProperties{
       if(secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32){
           throw new IllegalArgumentException("authsecret debe tener al menos 32 bytes");
       }
       if(!"servidos".equals(issuer)){
           throw new IllegalArgumentException("issuer debe ser 'servidos'");
       }
       if(!"servidos-web".equals(audience)){
           throw new IllegalArgumentException("audience debe ser 'servidos-web'");
       }
       if(accessTtl == null|| accessTtl.isZero() || accessTtl.isNegative()){
           throw new IllegalArgumentException("accessTtl debe ser un valor positivo");
       }
       if(refreshAbsoluteTtl == null || refreshAbsoluteTtl.isZero() || refreshAbsoluteTtl.isNegative()){
           throw new IllegalArgumentException("refreshAbsoluteTtl debe ser un valor positivo");
       }
    }




}
