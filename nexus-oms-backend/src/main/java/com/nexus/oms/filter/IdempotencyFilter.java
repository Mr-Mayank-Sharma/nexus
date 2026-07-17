package com.nexus.oms.filter;

import com.nexus.oms.service.IdempotencyService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Component
@Order(2)
public class IdempotencyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String method = req.getMethod().toUpperCase();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = req.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String redisKey = idempotencyService.hashKey(idempotencyKey);

        IdempotencyService.CachedResponse cached = idempotencyService.getCompleted(redisKey);
        if (cached != null) {
            log.debug("Idempotency hit for key={}", redisKey);
            res.setStatus(cached.statusCode());
            res.setContentType("application/json");
            res.getWriter().write(cached.body());
            return;
        }

        String currentStatus = idempotencyService.getStatus(redisKey);
        if (IdempotencyService.STATUS_CONFLICT.equals(currentStatus)) {
            log.warn("Idempotency conflict for key={}", redisKey);
            res.setStatus(409);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Conflict\",\"message\":\"Idempotency key already used with different request\"}");
            return;
        }

        boolean acquired = idempotencyService.tryAcquire(redisKey);
        if (!acquired) {
            log.warn("Idempotency already in progress for key={}", redisKey);
            res.setStatus(409);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Conflict\",\"message\":\"Request with this idempotency key is already in progress\"}");
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(req);
        CachedBodyHttpServletResponse wrappedResponse = new CachedBodyHttpServletResponse(res);

        try {
            chain.doFilter(wrappedRequest, wrappedResponse);

            int status = wrappedResponse.getStatus();
            String body = wrappedResponse.getCachedBody();

            if (status >= 200 && status < 300) {
                idempotencyService.complete(redisKey, status, body);
            } else {
                idempotencyService.delete(redisKey);
            }

            res.setStatus(status);
            res.setContentType(wrappedResponse.getContentType());
            res.getWriter().write(body);
        } catch (Exception e) {
            idempotencyService.delete(redisKey);
            throw e;
        }
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (var is = request.getInputStream()) {
                is.transferTo(baos);
            }
            this.cachedBody = baos.toByteArray();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                private final ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);

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

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    private static class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private PrintWriter writer;
        private boolean usingWriter;
        private int statusCode = 200;

        CachedBodyHttpServletResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.statusCode = sc;
            super.setStatus(sc);
        }

        @Override
        public int getStatus() {
            return statusCode;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(int b) {
                    baos.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (!usingWriter) {
                writer = new PrintWriter(baos, true, StandardCharsets.UTF_8);
                usingWriter = true;
            }
            return writer;
        }

        String getCachedBody() {
            if (usingWriter) {
                writer.flush();
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}
