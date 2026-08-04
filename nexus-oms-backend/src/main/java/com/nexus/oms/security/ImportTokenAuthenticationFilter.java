package com.nexus.oms.security;

import com.nexus.oms.service.ImportTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class ImportTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ImportTokenAuthenticationFilter.class);
    private static final String IMPORT_TOKEN_HEADER = "X-Import-Token";

    private final ImportTokenService importTokenService;

    public ImportTokenAuthenticationFilter(ImportTokenService importTokenService) {
        this.importTokenService = importTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(IMPORT_TOKEN_HEADER);
        if (StringUtils.hasText(token)) {
            ImportTokenService.ImportTokenPayload payload = importTokenService.validateToken(token);
            if (payload != null) {
                // Replay prevention: check if this token's jti has already been consumed
                String jti = payload.jti();
                if (jti != null && importTokenService.isNonceUsed(jti)) {
                    log.warn("Import token replay detected — jti={} already consumed", jti);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Token already used\"}");
                    return;
                }

                // Mark nonce as used (atomic Redis SETNX)
                if (jti != null && !importTokenService.tryMarkNonceUsed(jti, payload.exp())) {
                    log.warn("Import token replay detected during consume — jti={}", jti);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Token already used\"}");
                    return;
                }

                List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_IMPORT_TOKEN"));
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken("import-token", null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
