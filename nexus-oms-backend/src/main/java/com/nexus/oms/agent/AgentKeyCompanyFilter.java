package com.nexus.oms.agent;

import com.nexus.core.service.AgentKeyService;
import com.nexus.oms.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent requests ({@code /agent-commerce/}) come with no one signed in. Their key names the company
 * ({@code nxa_<company>.<random>}), so for the whole request the core works in that company's database (doc 29,
 * phase B). The key itself is still checked by the controller; a wrong company in a key only means "unknown key".
 * Keys without a company (issued before this) use the shared database.
 */
@Component
public class AgentKeyCompanyFilter extends OncePerRequestFilter {

    static final String AGENT_PATH = "/agent-commerce/";
    private static final String BEARER_PREFIX = "Bearer ";
    /** The company part of a key: 32 hex digits (a UUID without dashes) and the separator. */
    private static final Pattern COMPANY_TAG = Pattern.compile("^" + Pattern.quote(AgentKeyService.KEY_PREFIX) + "([0-9a-f]{32})"
            + Pattern.quote(String.valueOf(AgentKeyService.TAG_SEPARATOR)));

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith(AGENT_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Optional<UUID> companyId = companyOf(request.getHeader("Authorization"));
        if (companyId.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        TenantContext.setCurrentTenantId(companyId.get());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** The company a bearer key names, if it names one. */
    static Optional<UUID> companyOf(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        Matcher tag = COMPANY_TAG.matcher(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
        if (!tag.find()) {
            return Optional.empty();
        }
        String hex = tag.group(1);
        String withDashes = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-"
                + hex.substring(20);
        return Optional.of(UUID.fromString(withDashes));
    }
}
