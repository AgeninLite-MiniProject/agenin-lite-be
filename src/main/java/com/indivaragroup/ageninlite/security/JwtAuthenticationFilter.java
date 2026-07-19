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
            if (jwtUtil.isTokenValid(jwt)) {
                if (SecurityContextHolder.getContext().getAuthentication() != null) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Claims claims;
                try {
                    claims = jwtUtil.extractAllClaims(jwt);
                } catch (Exception e) {
                    sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020", "Invalid token claims");
                    return;
                }

                String tokenType = claims.get("type", String.class);
                if (!"access".equals(tokenType)) {
                    log.error("Filter blocked: Attempt to use non-access token");
                    sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020", "Invalid token type");
                    return;
                }

                String jtiStr = claims.getId();
                if (jtiStr == null) {
                    log.error("Filter blocked: Missing JTI claim");
                    sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020", "Missing JTI claim");
                    return;
                }

                UUID jti;
                try {
                    jti = UUID.fromString(jtiStr);
                } catch (IllegalArgumentException e) {
                    log.error("Filter blocked: Invalid JTI format");
                    sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020", "Invalid JTI format");
                    return;
                }

                if (jwtBlacklistRepository.existsByTokenJti(jti)) {
                    log.error("Filter blocked: Access token is blacklisted (logged out)");
                    sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0040", "Token is blacklisted");
                    return;
                }

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userId, null, Collections.singleton(authority));

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                log.error("Filter blocked: Invalid JWT signature/expiry");
                sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020", "Invalid token");
                return;
            }
        } catch (Exception e) {
            log.error("Filter blocked: Unexpected error", e);
            sendAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_0020", "Authentication failed");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void sendAuthError(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"error_code\":\"%s\", \"message\":\"%s\"}", errorCode, message));
    }
}
