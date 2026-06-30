package com.nexus.oms.service;

import com.nexus.oms.dto.*;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.entity.NxUser;
import com.nexus.oms.exception.BadRequestException;
import org.springframework.security.authentication.BadCredentialsException;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.repository.UserRepository;
import com.nexus.oms.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CompanySettingsRepository companySettingsRepository;

    private AuthService authService;
    private NxUser testUser;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, companySettingsRepository);
        tenantId = UUID.randomUUID();
        testUser = NxUser.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .username("testuser")
                .passwordHash("encoded-pass")
                .role("VIEWER")
                .build();
    }

    @Test
    void testRegister_CreatesUserAndReturnsAuth() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("VIEWER");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("Test1234!")).thenReturn("encoded-new");
        when(userRepository.save(any(NxUser.class))).thenAnswer(i -> {
            NxUser u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateToken(eq("newuser"), eq("VIEWER"), any())).thenReturn("test-token");

        AuthResponse result = authService.register(request);

        assertNotNull(result.getAccessToken());
        assertEquals("test-token", result.getAccessToken());
        verify(userRepository).save(any(NxUser.class));
    }

    @Test
    void testRegister_WithExistingUsername_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("Test1234!");
        request.setRole("VIEWER");

        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegister_WithInvalidRole_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("INVALID_ROLE");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testAuthenticate_WithValidCredentials_ReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Test1234!");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Test1234!", "encoded-pass")).thenReturn(true);
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateToken("testuser", "VIEWER", tenantId)).thenReturn("jwt-token");

        AuthResponse result = authService.authenticate(request);

        assertNotNull(result.getAccessToken());
        assertEquals("jwt-token", result.getAccessToken());
    }

    @Test
    void testAuthenticate_WithInvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong-pass");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong-pass", "encoded-pass")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.authenticate(request));
    }
}
