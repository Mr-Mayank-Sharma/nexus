package com.nexus.oms.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void doFilter_generatesRequestIdWhenHeaderMissing() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        doAnswer(invocation -> {
            assertNotNull(MDC.get("requestId"));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);
    }

    @Test
    void doFilter_usesHeaderValueWhenPresent() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("client-provided-id");

        doAnswer(invocation -> {
            assertEquals("client-provided-id", MDC.get("requestId"));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);
    }

    @Test
    void doFilter_cleansMdcAfterChain() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("test-id");

        filter.doFilter(request, response, chain);

        assertNull(MDC.get("requestId"));
    }

    @Test
    void doFilter_setsRequestIdEvenOnException() throws Exception {
        when(request.getHeader("X-Request-Id")).thenReturn("error-id");
        doThrow(new RuntimeException("chain error")).when(chain).doFilter(request, response);

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));

        assertNull(MDC.get("requestId"));
    }
}
