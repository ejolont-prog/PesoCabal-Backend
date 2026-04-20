package com.example.pesoCabal.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Obtener el encabezado de Authorization
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // 2. Validar el token
                if (jwtUtil.isTokenValid(token)) {
                    // 3. Extraer los datos del usuario (Claims)
                    Claims claims = jwtUtil.getClaims(token);
                    String username = claims.getSubject();

                    // 4. Crear la autenticación para Spring Security
                    // Usamos una lista vacía de permisos (roles) por ahora
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            Collections.emptyList()
                    );

                    // 5. Establecer la autenticación en el contexto de seguridad
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // 6. Dejar que la petición continúe
                    filterChain.doFilter(request, response);
                    return;
                }
            } catch (Exception e) {
                // Si algo falla al procesar el token, el flujo caerá al error 401
            }
        }

        // 7. Si llega aquí es porque no hubo token o fue inválido
        // IMPORTANTE: Permitir que rutas públicas (como Swagger) pasen sin token
        String path = request.getServletPath();
        if (path.contains("/swagger-ui") || path.contains("/v3/api-docs") || path.contains("/public")) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token invalido o ausente\"}");
        }
    }
}