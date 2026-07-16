package com.nexus.oms.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilter_setsAllSixSecurityHeaders() throws IOException, ServletException {
        filter.doFilter(request, response, filterChain);

        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("X-Frame-Options", "DENY");
        verify(response).setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        verify(response).setHeader("X-XSS-Protection", "1; mode=block");
        verify(response).setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
    }

    @Test
    void doFilter_headersHaveCorrectValues() throws IOException, ServletException {
        filter.doFilter(request, response, filterChain);

        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setHeader("X-Frame-Options", "DENY");
        verify(response).setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        verify(response).setHeader("X-XSS-Protection", "1; mode=block");
        verify(response).setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        verify(response).setHeader("Pragma", "no-cache");
    }

    @Test
    void doFilter_chainsToNextFilter() throws IOException, ServletException {
        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_exactlySixHeadersSet() throws IOException, ServletException {
        filter.doFilter(request, response, filterChain);

        verify(response, times(6)).setHeader(anyString(), anyString());
    }
}
