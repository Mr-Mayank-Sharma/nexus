package com.nexus.oms.exception;

import com.nexus.oms.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidation_returns400WithFieldErrors() {
        FieldError fe1 = new FieldError("order", "email", "must not be blank");
        FieldError fe2 = new FieldError("order", "quantity", "must be positive");

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fe1, fe2));

        org.springframework.core.MethodParameter param = mock(org.springframework.core.MethodParameter.class);
        when(param.getParameterIndex()).thenReturn(0);
        when(param.getContainingClass()).thenReturn((Class) Object.class);
        when(param.getExecutable()).thenReturn(null);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("errors"));
        assertTrue(response.getBody().containsKey("timestamp"));

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertEquals("must not be blank", errors.get("email"));
        assertEquals("must be positive", errors.get("quantity"));
    }

    @Test
    void handleNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order", 123L);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Order not found with id: 123", response.getBody().getMessage());
    }

    @Test
    void handleNotFound_withStringMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Product not found");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Product not found", response.getBody().getMessage());
    }

    @Test
    void handleAccessDenied_returns403() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("denied");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Access denied", response.getBody().getMessage());
    }

    @Test
    void handleBadRequest_returns400() {
        BadRequestException ex = new BadRequestException("Invalid order data");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid order data", response.getBody().getMessage());
    }

    @Test
    void handleGeneric_returns500() {
        Exception ex = new RuntimeException("something unexpected");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Internal server error", response.getBody().getMessage());
    }

    @Test
    void handleBadCredentials_returns401() {
        org.springframework.security.authentication.BadCredentialsException ex =
                new org.springframework.security.authentication.BadCredentialsException("bad");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentials(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid username or password", response.getBody().getMessage());
    }
}
