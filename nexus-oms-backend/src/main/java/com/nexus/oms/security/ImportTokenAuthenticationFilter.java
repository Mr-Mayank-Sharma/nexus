package com.nexus.oms.security;

import com.nexus.oms.service.ImportTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
