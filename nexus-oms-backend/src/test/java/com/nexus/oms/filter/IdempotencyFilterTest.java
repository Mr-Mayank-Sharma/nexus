package com.nexus.oms.filter;

import com.nexus.oms.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private IdempotencyFilter filter;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new IdempotencyFilter(idempotencyService);
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void doFilter_skipsNonMutatingMethods() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(idempotencyService, never()).hashKey(anyString());
    }

    @Test
    void doFilter_skipsWhenNoHeader() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_returnsCachedResponseWhenCompleted() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-1");
        when(idempotencyService.hashKey("key-1")).thenReturn("idempotent:abc");
        when(idempotencyService.getCompleted("idempotent:abc"))
                .thenReturn(new IdempotencyService.CachedResponse(200, "{\"id\":\"cached\"}"));

        filter.doFilter(request, response, chain);

        verify(response).setStatus(200);
        verify(response).setContentType("application/json");
        assertTrue(responseWriter.toString().contains("cached"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_returnsConflictWhenPreviousConflict() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-1");
        when(idempotencyService.hashKey("key-1")).thenReturn("idempotent:abc");
        when(idempotencyService.getCompleted("idempotent:abc")).thenReturn(null);
        when(idempotencyService.getStatus("idempotent:abc")).thenReturn("CONFLICT");

        filter.doFilter(request, response, chain);

        verify(response).setStatus(409);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_returnsConflictWhenInProgress() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-1");
        when(idempotencyService.hashKey("key-1")).thenReturn("idempotent:abc");
        when(idempotencyService.getCompleted("idempotent:abc")).thenReturn(null);
        when(idempotencyService.getStatus("idempotent:abc")).thenReturn("PENDING");
        when(idempotencyService.tryAcquire("idempotent:abc")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(409);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void doFilter_newRequestProcessesAndCaches() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-1");
        when(request.getInputStream()).thenReturn(createServletInputStream("{\"order\":\"new\"}"));
        when(idempotencyService.hashKey("key-1")).thenReturn("idempotent:abc");
        when(idempotencyService.getCompleted("idempotent:abc")).thenReturn(null);
        when(idempotencyService.getStatus("idempotent:abc")).thenReturn(null);
        when(idempotencyService.tryAcquire("idempotent:abc")).thenReturn(true);

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(201);
            res.setContentType("application/json");
            res.getWriter().write("{\"id\":\"order-1\"}");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response, times(2)).setStatus(201);
        verify(idempotencyService).complete(eq("idempotent:abc"), eq(201), contains("order-1"));
    }

    @Test
    void doFilter_onErrorDeletesKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-1");
        when(request.getInputStream()).thenReturn(createServletInputStream("{}"));
        when(idempotencyService.hashKey("key-1")).thenReturn("idempotent:abc");
        when(idempotencyService.getCompleted("idempotent:abc")).thenReturn(null);
        when(idempotencyService.getStatus("idempotent:abc")).thenReturn(null);
        when(idempotencyService.tryAcquire("idempotent:abc")).thenReturn(true);
        doThrow(new RuntimeException("chain error")).when(chain).doFilter(any(), any());

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, chain));

        verify(idempotencyService).delete("idempotent:abc");
        verify(idempotencyService, never()).complete(anyString(), anyInt(), anyString());
    }

    @Test
    void doFilter_onNonSuccessDeletesKey() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("key-1");
        when(request.getInputStream()).thenReturn(createServletInputStream("{\"bad\":\"request\"}"));
        when(idempotencyService.hashKey("key-1")).thenReturn("idempotent:abc");
        when(idempotencyService.getCompleted("idempotent:abc")).thenReturn(null);
        when(idempotencyService.getStatus("idempotent:abc")).thenReturn(null);
        when(idempotencyService.tryAcquire("idempotent:abc")).thenReturn(true);
        when(response.getContentType()).thenReturn("application/json");

        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(400);
            res.getWriter().write("{\"error\":\"bad request\"}");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        verify(response, atLeastOnce()).setStatus(400);
        verify(idempotencyService).delete("idempotent:abc");
        verify(idempotencyService, never()).complete(anyString(), anyInt(), anyString());
    }

    private ServletInputStream createServletInputStream(String body) {
        ByteArrayInputStream bais = new ByteArrayInputStream(body.getBytes());
        return new ServletInputStream() {
            @Override
            public int read() {
                return bais.read();
            }
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }
            @Override
            public boolean isReady() {
                return true;
            }
            @Override
            public void setReadListener(ReadListener listener) {
            }
        };
    }
}
