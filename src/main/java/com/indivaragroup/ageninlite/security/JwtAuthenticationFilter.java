package com.indivaragroup.ageninlite.security;

import com.indivaragroup.ageninlite.repository.auth.JwtBlacklistRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final JwtBlacklistRepository jwtBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (jwtUtil.isTokenValid(jwt) && SecurityContextHolder.getContext().getAuthentication() == null) {
                Claims claims = jwtUtil.extractAllClaims(jwt);

                String tokenType = claims.get("type", String.class);
                if (!"access".equals(tokenType)) {
                    log.error("Filter blocked: Attempt to use non-access token");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020 - Invalid token type");
                    return;
                }
                String jti = claims.getId();
                if (jti != null && jwtBlacklistRepository.existsByTokenJti(UUID.fromString(jti))) {
                    log.error("Filter blocked: Access token is blacklisted (logged out)");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0040 - Token is blacklisted");
                    return;
                }

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userId, null, Collections.singleton(authority));

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.error("Gagal memvalidasi JWT Token: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
