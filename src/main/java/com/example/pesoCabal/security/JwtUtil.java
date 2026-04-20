package com.example.pesoCabal.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    public Claims getClaims(String token) {
        // Convertimos el String a una Key segura para HS256
        Key key = Keys.hmacShaKeyFor(secretKey.getBytes());
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }


    public Long getUserIdFromToken(String token) {
        // Si el token viene con el prefijo "Bearer ", lo quitamos
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Claims claims = getClaims(token);
        // Extraemos el ID que guardamos con la clave "idUsuario"
        // Es importante usar el mismo nombre que pusiste en generateToken
        return claims.get("idUsuario", Long.class);
    }

}